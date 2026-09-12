# Discovering new Scala organisations on Maven Central

Research notes — 2026-09-02. Context: artifacts under new namespaces (e.g.
`com.halotukozak`) never appear in Scaladex even though they are live on Maven
Central, are valid Scala 3 / Scala.js / Native artifacts, and would be useful for
the community build regression net (scala/scala3#26958).

This document describes **how discovery works today**, **why it misses new
namespaces**, **what Maven Central actually offers as an API**, and a
**recommended pipeline** to close the gap.

---

## 1. How Scaladex ingests artifacts today

Every artifact that enters the index goes through exactly one function:

`PublishProcess.publishPom` → `ArtifactConverter.convert` → `artifactService.insertArtifact`
(`modules/server/src/main/scala/scaladex/server/service/PublishProcess.scala`).

There are only four callers:

| Caller | Trigger | Can discover a brand-new groupId? |
|---|---|---|
| `PublishApi` `PUT /publish` | external HTTP push (sbt plugin / CI / Sonatype) | **yes** |
| `MavenCentralService.findMissing` | job `missing-maven-artifacts`, every 24h | no — iterates `database.getGroupIds()` |
| `MavenCentralService.findNonStandard` | job `non-standard-artifacts`, every 2h | no — fixed list from `non-standard.json` |
| `AdminService.findMissingArtifacts` / `syncOne` | manual admin action, groupId typed by hand | no (human supplies the groupId) |

`database.getGroupIds()` is `SELECT DISTINCT group_id FROM artifacts`
(`SqlDatabase.getGroupIds` → `ArtifactTable.selectGroupIds`). So the periodic
Maven Central sync is a **closed loop**: it can only re-scan namespaces that are
*already* in the index. It backfills missing versions/artifacts of known groups;
it can never see a new group.

### The Maven Central client is a directory scraper

`MavenCentralClientImpl` (`modules/infra/.../MavenCentralClientImpl.scala`) has
three operations, all against `https://repo1.maven.org/maven2`:

- `getAllArtifactIds(groupId)` — GET `…/<group/as/path>/`, parse the Apache
  autoindex HTML with `JsoupUtils.listDirectories`.
- `getAllVersions(groupId, artifactId)` — same, one level deeper.
- `getPomFile(ref)` — GET the `.pom`.

There is **no** call to any search/enumeration API. Given a groupId it works
fine — e.g. `https://repo1.maven.org/maven2/com/halotukozak/` returns
`alpaca_3/`, `commons_3/`, `mcodec_sjs1_3/`, … right now. The missing piece is
purely *knowing the groupId exists*.

### Where new groups used to come from

Historically Scaladex "receives poms automatically from Maven Central (Sonatype)"
(README) via an OSSRH/Sonatype post-deploy notification hitting `PUT /publish`,
plus an initial bulk seed (Bintray + the Central Lucene index). With the OSSRH →
**Central Portal** migration, publishers that use the Portal (its own
`central-ossrh` upload or the Portal API) no longer trigger that notification.
`com.halotukozak` publishes through the Portal → nothing calls `/publish` → the
namespace is invisible to Scaladex forever.

The `central-ossrh` username handled in `PublishApi.authenticateUser` covers the
case where a publisher *also* points their build at `index.scala-lang.org`, but
that is opt-in and most people don't do it.

---

## 2. Maven Central data sources (surveyed & tested 2026-09-02)

### 2.1 `repo1.maven.org` autoindex HTML — *current*
- Pros: authoritative, always up to date, no auth, already wired in.
- Cons: no enumeration of top-level groups, no "what changed" feed, one HTTP
  request per directory. Rate-limited (see recent commits lowering the throttle
  to 1 req/s + retry caps).

### 2.2 Legacy Solr API — `https://search.maven.org/solrsearch/select` — **unreliable**
- `?q=g:com.halotukozak&core=gav&wt=json` → `numFound: 0` today, although the
  artifacts are live on `repo1` and on `central.sonatype.com`.
- The legacy Solr index lags badly / omits many Central Portal publishers.
  Multiple upstream issues about `sort` being ignored and versions ordered by
  name not date.
- Default sort already is `score desc, timestamp desc, …`; leading-wildcard
  queries (`a:*_3`) return HTTP 400.
- **Verdict: not trustworthy as a discovery source in 2026.**

### 2.3 Central Portal browse API — `POST https://central.sonatype.com/api/internal/browse/components` — **works, undocumented**

This backs the search box on `central.sonatype.com`.

```
POST https://central.sonatype.com/api/internal/browse/components
content-type: application/json

{"page":0,"size":20,"sortField":"publishedDate","sortDirection":"desc"}
```

Returns, newest first across *all* of Maven Central:

```json
{"pageCount":667,"totalResultCount":10000,"components":[
  {"namespace":"io.github.makingthematrix","name":"signals3_3",
   "projectName":"signals3","latestVersionInfo":{"version":"1.2.1","timestampUnixWithMS":1788366045000,"licenses":["MIT"]},
   "ec":["-sources.jar",".pom","-javadoc.jar",".jar"],"packaging":"jar", ...}
]}
```

- A global **"recently published" feed** — exactly what discovery needs. A single
  page already surfaced a Scala 3 artifact (`signals3_3`).
- `filter`/`searchTerm` also allow `namespace:<group>` scoped queries.
- Caveats: `/api/internal/` = **no stability guarantee**; result window capped at
  10 000; small page size (20 OK, 50 → HTTP 400); no published rate limits.
- Good as a *low-latency signal*, risky as the *only* source.

### 2.4 Central Portal published API — `https://central.sonatype.com/api/v1/...`
- Documented, but scoped to **publishing** (upload, deployment status) and to
  *your own* namespaces. No public "search everything" endpoint documented.
- Not useful for third-party discovery.

### 2.5 Maven Central Index (`nexus-maven-repository-index`) — the "IntelliJ" approach — **deep dive**

This is the Apache **maven-indexer** published index: a decade-old mechanism used
by Nexus, Artifactory, Eclipse m2e, and IntelliJ's *"Maven repository index"*
feature. Everything below was verified live on 2026-09-02 (parsed a real chunk
byte-for-byte).

#### 2.5.1 What Maven Central publishes

At `https://repo1.maven.org/maven2/.index/` (mirror: `repo.maven.apache.org`):

| File | Purpose | Size (2026-09-01) |
|---|---|---|
| `nexus-maven-repository-index.properties` | control file / cursor | ~2 KB |
| `nexus-maven-repository-index.gz` | **full** index (one chunk) | **3.24 GB** |
| `nexus-maven-repository-index.<N>.gz` | incremental chunk `N` | ~1–45 MB |

`repo1` currently retains chunks **323 … 936** (614 files). The `.properties`:

```
nexus.index.id=central
nexus.index.chain-id=1318453614498          # identity of the whole chunk chain
nexus.index.timestamp=20260901193333        # publish time of the newest chunk
nexus.index.last-incremental=936            # newest chunk number
nexus.index.incremental-0=936               # sliding window of recent chunk ids
nexus.index.incremental-1=935               # (…-0 … -29), used to decide whether
...                                         # an incremental catch-up is still possible
nexus.index.incremental-29=907
```

#### 2.5.2 Wire format (no Lucene, no library needed)

Each `.gz` is a gzip stream wrapping a trivial binary framing
(`org.apache.maven.index.reader.ChunkReader`):

```
byte    version            # always 1
long    timestamp (ms)      # index time of this chunk
repeat until EOF:
  int   fieldCount
  repeat fieldCount times:
    byte   flags            # ignored
    UTF    name             # java modified-UTF-8, 2-byte length  (field names are ASCII)
    int    valueLength
    bytes  value            # java modified-UTF-8
```

Records are untyped maps; the kind is inferred from which key is present
(`RecordExpander`):

| Key present | Meaning | Fields that matter |
|---|---|---|
| `u` | **artifact added** | `u = groupId\|artifactId\|version\|classifier-or-"NA"[\|ext]`, `i = packaging\|fileMtime\|size\|hasSrc\|hasDoc\|hasSig\|ext`, `n` name, `d` description, `1` sha1, `classnames` (bulk), plugin/OSGi extras |
| `del` | **artifact removed** | `del` = same UINFO layout, `m` = timestamp |
| `allGroups` / `allGroupsList` | full list of every groupId | **empty in incremental chunks — only populated in the full index** |
| `rootGroups` / `rootGroupsList` | first path segment of every groupId | same caveat |
| `DESCRIPTOR` | repo id | — |

Real records pulled from chunk 323:

```
add: u=xyz.danoz|recyclerviewfastscroller|0.1.3|NA   i=aar|1429833125000|19638|1|1|1|aar  n=RecyclerViewFastScroller
del: del=org.siani|magritte|1.1.2|javadoc|jar        m=1430071994196
```

A complete parser is **~30 lines of Scala** (`GZIPInputStream` + `DataInputStream`
+ the loop above). For discovery you only read `u` / `del` and ignore
`classnames` (which is most of the bytes). Alternatively depend on
`org.apache.maven.indexer:indexer-reader:7.1.6` — it is genuinely dependency-free
(no Lucene, no Plexus) and gives you `IndexReader` / `ChunkReader` / `Record` /
`RecordExpander`.

#### 2.5.3 Incremental / cursor mechanism

`IndexReader(local, remote)` reads both `.properties` files and decides:

- `chain-id` differs, or the gap since your `last-incremental` has scrolled out
  of the `incremental-0..29` window → **full re-pull** (`…index.gz`).
- otherwise → fetch chunks `localLastIncremental+1 … remoteLastIncremental`,
  iterate their records, and on `close()` write the remote `.properties` back to
  `local` (that is the commit of the cursor).

For Scaladex the "local" side is a single DB row: `(chain_id, last_incremental)`.
A custom implementation doesn't need the `ResourceHandler` SPI at all — just
GET the `.properties`, compare, GET the missing `.N.gz`, parse, update the row.

#### 2.5.4 Freshness — the catch (verified from chunk timestamps)

| chunk | index time | compressed size |
|---|---|---|
| 323 | 2015-04-26 | 1.1 MB |
| 800 | 2023-04-08 | — |
| 900 | 2025-08-17 | 8.5 MB |
| 920 | 2026-03-11 | — |
| 930 | 2026-06-20 | 43 MB |
| 934 | 2026-07-23 | — |
| 936 | 2026-09-01 | 41 MB |

⇒ **the publish cadence is ~1–4 weeks and irregular, trending slower.** It is
*not* daily (the `.properties` `last-modified` misleads — only the newest chunk's
timestamp matters). A namespace that first publishes today can take up to a month
to appear in this index. Chunk size has grown with Maven Central's throughput:
now ~40 MB per (roughly monthly) chunk ≈ **40–170 MB/month** of transfer once
seeded.

#### 2.5.5 One-time seed cost

The full index is **3.24 GB** to download once, then a streaming parse (most
bytes are `classnames`/`d`/`n` you discard). There is no lighter "all groupIds"
file — `allGroupsList` only exists *inside* that 3.2 GB blob. **Better seed:** skip
it. Scaladex already indexes a large fraction of the ecosystem; set the cursor to
today's `last-incremental` and only catch *future* first-publishers, with a
Central Portal feed pass (§2.3) to backfill the recent weeks.

#### 2.5.6 How IntelliJ actually uses this — and why it's a cautionary tale

IntelliJ's *"Maven repository index"* is exactly this nexus index via
maven-indexer + **Lucene**. For Maven Central it is **disabled / manual by
default**: the multi-GB download plus building the local Lucene index costs too
much disk and RAM. JetBrains steered users to **Package Search** (a separate
hosted API) instead — which was then **shut down in April 2025**. Current IntelliJ
falls back to `~/.m2` local completion plus on-demand Central REST calls.

So the project most associated with the full-index approach has spent a decade
migrating *away* from it. Scaladex should take the mechanism (incremental chunks
are a solid authoritative delta feed, including deletes) but not the framing
(don't download 3.2 GB, don't pull in Lucene, don't treat it as low-latency).

### 2.6 Third-party: libraries.io, ecosyste.ms, deps.dev
- `deps.dev` (Google) and `ecosyste.ms` both expose Maven package lists + APIs
  and are reasonably current. Usable as a cross-check / backfill, adds an
  external dependency and their own lag.

---

## 3. Recommended approach

Add a **discovery stage** in front of the existing sync. Its only job is to
produce candidate `groupId`s; everything downstream already works
(`MavenCentralService.syncOne` → `getAllArtifactIds` → `getPomFile` →
`publishPom` → `scm` → project).

```
  ┌──────────────────────────┐        ┌──────────────────────────┐
  │ Tier 1 — FRESHNESS       │        │ Tier 2 — COMPLETENESS    │
  │ Central Portal browse    │        │ nexus incremental index  │
  │ POST …/browse/components │        │ .index/*.N.gz since       │
  │ sortField=publishedDate  │        │ (chain_id,last_incremental)│
  │ poll ~hourly             │        │ poll ~weekly              │
  │ near-real-time, cheap    │        │ authoritative + deletes  │
  │ undocumented → defensive │        │ ~40 MB/chunk, ~monthly    │
  └───────────┬──────────────┘        └───────────┬──────────────┘
              └───────────────┬────────────────────┘
                              ▼
              Scala filter — reuse core/model/Artifact.scala:
              Artifact.ArtifactId.parse(a).isScala && .binaryVersion.isValid
              (matches _2.13, _3, _sjs1_3, _native0.5_3, _2.12_1.0, …)
                              ▼
              groupId ∉ database.getGroupIds()  ?
                              ▼
              ┌─────────────────────────────────────────┐
              │ discovered_group_ids                    │
              │  group_id, source, first_seen,          │
              │  status(pending|synced|no_github|error),│
              │  last_synced, artifact_count            │
              └─────────────────┬───────────────────────┘
                                ▼
              job: for N pending → mavenCentralService.syncOne(g, None)
```

### Why two tiers

- The nexus index is **authoritative and complete** (and is the only source that
  reports **deletions**), but it lags **1–4 weeks** (§2.5.4). Too slow on its own
  for "a new org just appeared".
- The Portal feed is **near-real-time and cheap**, but it is an **undocumented
  `/api/internal/` endpoint**, capped at 10 000 results, and could change or
  vanish. Fine as a fast path, not as the system of record.
- Together: the feed catches new namespaces within the hour; the weekly index
  pass reconciles anything the feed dropped and keeps the picture honest.
- **Legacy Solr (`search.maven.org`) is not used at all** — §2.2.

### Do NOT

- Download the 3.24 GB full index. Seed the cursor at today's `last-incremental`
  and let Tier 1 backfill recent weeks (§2.5.5).
- Pull in `maven-indexer` core / Lucene. Either a ~30-line `ChunkReader` (§2.5.2)
  or `indexer-reader` (dependency-free).
- Hammer `repo1` autoindex for discovery — it is being actively rate-limited
  (recent commits dropped it to 1 req/s). Discovery reads CDN files
  (`.index/*.gz`) and one JSON endpoint; the per-group crawl stays inside the
  existing throttled `MavenCentralClient`.

### New pieces

| Layer | Addition |
|---|---|
| `core/shared/.../service` | `MavenCentralIndexClient` trait (`fetchCursor`, `chunksSince(cursor)`), `MavenCentralPortalClient` trait (`recentComponents(since): Seq[GroupId]`) |
| `infra` | `MavenCentralIndexClientImpl` (GET `.properties` + `.N.gz`, gzip+DataInputStream parse), `MavenCentralPortalClientImpl` (POST browse, pekko-http, defensive) |
| `infra` (SQL) | `discovered_group_ids` table + Flyway migration; `index_cursor` row; `SchedulerDatabase`: `insertDiscoveredGroupIds`, `getPendingDiscoveredGroupIds(limit)`, `markDiscovered(groupId, status)`, `get/setIndexCursor` |
| `server/.../service` | `DiscoveryService`: run each tier → Scala-filter → diff vs `getGroupIds()` → upsert `discovered_group_ids` → drain N pending via `mavenCentralService.syncOne` |
| `server/.../service/AdminService` | jobs (guard `!env.isLocal`): `discover-portal-feed` ~1h, `discover-index-chunks` ~24h (does work only when a new chunk landed) |
| `view` + `route/AdminPage` | table of discovered namespaces + status counters + "sync now" button (clone of the existing `findMissingArtifacts` task UI) |

### Sketch — the chunk reader (Tier 2 core, ~all of the custom code)

```scala
// infra: parse one nexus-maven-repository-index.<N>.gz stream
final case class IndexEntry(groupId: String, artifactId: String, version: String, deleted: Boolean)

def readChunk(in: InputStream): LazyList[IndexEntry] =
  val d = new DataInputStream(new GZIPInputStream(in, 8192))
  d.readByte()            // version (== 1)
  d.readLong()            // chunk timestamp
  def loop(): LazyList[IndexEntry] =
    val fieldCount =
      try d.readInt() catch case _: EOFException => -1
    if fieldCount < 0 then { d.close(); LazyList.empty }
    else
      val m = collection.mutable.Map.empty[String, String]
      for _ <- 0 until fieldCount do
        d.readByte()                       // flags
        val name = d.readUTF()             // ASCII field name
        val len = d.readInt()
        val buf = new Array[Byte](len); d.readFully(buf)
        m(name) = new String(buf, UTF_8)   // ok: coordinates are ASCII/BMP
      val uinfo = m.get("u").orElse(m.get("del"))
      uinfo match
        case Some(u) =>
          u.split('|') match
            case Array(g, a, v, _*) => IndexEntry(g, a, v, m.contains("del")) #:: loop()
            case _                  => loop()
        case None => loop()                // allGroups / rootGroups / DESCRIPTOR
  loop()
```

Discovery then = `readChunk` → keep entries where
`Artifact.ArtifactId.parse(artifactId)` is Scala & valid → distinct `groupId` not
in `getGroupIds()`.

### Guard rails

- A new Scala namespace can still have hundreds of artifacts → reuse the existing
  paging/delay machinery in `MavenCentralService` (`processPages`, `pageDelay`,
  `publishDelay`); don't sync all discovered groups in one tick — cap N per run.
- Namespaces whose POMs have no GitHub `scm` produce `NoGithubRepo` and no
  project — record the outcome so they aren't retried forever.
- Respect the same Maven Central `HttpClientConfig` throttle already tuned in
  recent commits.

---

## 4. Quick experiment to validate (no code in Scaladex needed)

Reproduces both tiers in a scratch script; ~1 h of work.

**Tier 2 (index):**
1. `curl -s .../.index/nexus-maven-repository-index.properties` → read
   `last-incremental` and `incremental-0..29`.
2. Download the last ~5 incremental chunks (`…index.<N>.gz`, ~40 MB each).
3. Parse with the §2.5.2 framing (30 lines, any language) — emit `u` / `del`.
4. Keep `u` where the artifactId parses as a valid Scala binary version;
   collect distinct groupIds; subtract what `index.scala-lang.org` already knows
   (`GET https://index.scala-lang.org/api/…` or a DB dump).
5. The residual set = "new Scala orgs we're missing". Expect `com.halotukozak`
   only if it published in the covered window (it may be too recent — see Tier 1).

**Tier 1 (Portal feed):**
```
curl -s -X POST https://central.sonatype.com/api/internal/browse/components \
  -H 'content-type: application/json' \
  -d '{"page":0,"size":20,"sortField":"publishedDate","sortDirection":"desc"}' \
  | jq '.components[] | {ns:.namespace, a:.name, t:.latestVersionInfo.timestampUnixWithMS}'
```
Page back (`page: 0..N`, `size` ≤ 20 — 50 returns HTTP 400) until timestamps pass
your last cursor; Scala-filter the `name`s; diff groupIds as above.

Compare the two residual sets — Tier 1 should be a superset of "very recent",
Tier 2 the authoritative "everything up to ~a month ago".

---

## 4b. Experiment results (run 2026-09-02)

Both tiers implemented as a throwaway Python script (`scratchpad/discover.py`).
The dedup mirrors `database.getGroupIds()`: a groupId counts as *known* if
Scaladex's API returns any version for **any** of its artifacts
(`GET index.scala-lang.org/api/v1/artifacts/<g>/<a>` → non-empty array).

### Tier 2 — nexus index, chunks 935 + 936

| | |
|---|---|
| chunk 935 / 936 index time | 2026-08-13 / 2026-09-01 (**19 days** between two publishes) |
| download | 44.7 MB + 40.6 MB |
| records parsed | 2 012 362 + 1 776 937 = **3.79 M** artifact rows, **0 deletes** |
| Scala artifacts (suffix filter) | **109 258** |
| distinct groupIds publishing Scala | **385** |
| **groupIds absent from Scaladex** | **140 (36 %)** |

Composition of the 140:

- **~53** are `io.github.*` / `com.github.*` — mostly one-person projects using
  `sbt-ci-release`, never registered with Scaladex.
- **~40** are noise for the community-build purpose: LLM/agent SDKs
  (`ai.fastllm`, `com.tjclp`, `io.repofyr`, `com.anymindgroup` zio-gcp-*,
  `io.github.zyblw`…) and Spark/data connectors
  (`io.substrait`, `io.graphframes`, `org.lance`, `dev.vortex`,
  `com.motherduck`, `tech.ytsaurus.spyt`, `org.voltdb`, `jp.co.yahoo.yosegi`,
  `org.apache.datafusion` Comet, `org.apache.xtable`…).
- **~45 are real, ordinary Scala libraries that simply never got in**, e.g.:
  `com.softwaremill.sttp.ai`, `com.softwaremill.chimp`, `org.polyvariant`
  (smithy-ts-codegen), `com.kevel` (apso — moved from `com.velocidi`),
  `com.colisweb` (scala-distances), `org.mongo4s`, `com.magine` (http4s-aws),
  `me.romac` (postino), `works.iterative`, `io.onfhir`, `dev.propensive`,
  `io.parapet`, `de.rmgk.slips`, `org.apalache-mc`, `org.finos.morphir.mill`,
  `net.scalax.*`, `fr.maif` (otoroshi), `com.snowflake` (snowpark),
  `dev.valentiay` (phobos fork).

Spot-checking 20 of the 140 by hand (probe plain `_3` / `_2.13` of the first base
names): **0 false positives** — all 20 were genuinely unknown. The earlier
"38–40 %" from a naïve "first 6 artifactIds" probe *did* have false positives
(`org.typelevel`, whose window artifacts were `scalac-compat-features_*`, which
Scaladex lacks); the wider probe fixed that and the number barely moved.

### Tier 1 — Central Portal feed, 500 most-recent components

3 groupIds with Scala artifacts; **1 real gap**: `dev.valentiay` (phobos, 29
Scala artifacts). `edu.gemini` (lucuma 0.242.0) and `io.github.makingthematrix`
(signals3_3 1.2.1) are *known groupIds* — pure `missing-maven-artifacts` sync
lag, not discovery gaps. Confirms Tier 1's role: catch brand-new namespaces fast,
let Tier 2 / the existing job handle new versions of known ones.

### Takeaways

1. **The gap is real and large.** In 19 days, ~140 Scala-publishing namespaces
   were invisible to Scaladex — direct confirmation that no automatic
   Maven Central → Scaladex path survives.
2. **A filter beyond the binary-version suffix is needed.** ~60 % of raw hits are
   `io.github.*` hobby repos or LLM/Spark-glue noise. Options: require a GitHub
   `scm` that resolves (the existing `syncOne` already drops the rest as
   `NoGithubRepo`), and/or a minimum artifact/vote threshold, and/or a
   review queue in the admin UI rather than auto-insert.
3. **Both tiers are cheap.** Tier 1 = ~25 JSON POSTs. Tier 2 = ~85 MB/run,
   ~4 M rows parsed in seconds by 30 lines of code, roughly monthly.
4. Numbers scale: 385 groupIds × ~1 chunk-pair/month ⇒ order-of-1000s of new
   candidate namespaces/year, ~a third unknown — but only a small fraction worth
   auto-indexing.

Full ranked list: `scratchpad/missing_groupids.txt`.

---

## 5. Immediate unblock for a single namespace (manual, works today)

Admin → "Find missing artifacts" with a Group ID (no artifact name) runs
`MavenCentralService.syncOne` → if the artifacts carry an `scm` pointing at a
public GitHub repo, projects get created. This is the per-namespace stopgap while
the discovery pipeline is built.

`com.halotukozak` was already onboarded this way — as of 2026-09-02 it resolves
(`index.scala-lang.org/api/v1/artifacts/com.halotukozak/commons_3` → project
`halotukozak-com/commons`, versions `0.1.0`–`0.1.3`).

---

## 6. Deciding what to index (the "noise" question)

Discovery is the easy half. The 140 missing groupIds from §4b are ~40 % junk for
the community-build purpose (LLM/agent scaffolding, Spark/Databricks connectors,
`io.github.*` experiments) and ~30 % ordinary Scala libraries — and **Maven
metadata barely tells them apart**.

### Signal strength (measured on the §4b sample, chunk 936: 87 missing vs 154 known)

| Signal | Missing vs Known | Cost | Verdict |
|---|---|---|---|
| valid Scala binary version | — | free | base filter (already have) |
| `<description>` present | 100 % vs 98 % | free | **useless** — Central makes it mandatory |
| ships `-sources.jar` | ~universal both sides | free | useless |
| ≥ 3 cross-published modules | 50 % vs 62 % | free | weak |
| ≥ 2 binary versions (`_2.13`+`_3`) | moderate | free | one-offs publish a single target |
| groupId is `io.github.*` / `com.github.*` | 34 % vs 16 % | free | weak — **do not hard-exclude**, Scaladex is full of legit ones |
| artifactId `~ *-spark[34]* / *_*.dbr_* / *-agent* / *-mcp*` | precise-ish | free | good for *de-prioritising* ~15–20 of the 140, risky as a hard filter |
| `<scm>` resolves to a live public GitHub repo | **strong** | 1 POM req (`syncOne` already does this) | **the real gate** |
| GitHub Scala % > 0 | strong | 1 GH API req | `addEmptyProject` already uses this |
| GitHub stars ≥ N | strong — but filters exactly the `com.halotukozak` profile | 1 GH API req | use for **ranking / surfacing, never for gating** |
| pushed within ~18 months | strong for "alive" | same GH call | ranking |

25 of 27 hand-checked missing groupIds had a resolvable GitHub `scm`
(`propensive/proscala`, `scala-native/scala-native`, `softwaremill/sttp-ai`, …) —
so the scm gate keeps almost all of them, which matches how Scaladex already
treats the publish webhook.

### Recommended policy — two independent knobs

1. **Indexing gate — inclusive, identical to today's publish path.**
   scm resolves to a live public GitHub repo **and** GitHub Scala % > 0 → auto
   `syncOne`. This lets in phobos, apso, scala-distances, sttp.ai *and* `zio-nn`
   and `fast-agent`. That is fine — Scaladex is a catalogue, and the
   regression-net value of a project like `com.halotukozak` is unrelated to its
   star count.
2. **Surfacing gate — strict.** A discovered project stays off the homepage /
   "new projects" / trending until it has GitHub info **and** (stars ≥ ~2 **or** a
   maintainer claimed it **or** an admin approved it). Add a `discovered`
   provenance flag next to `GithubStatus`; keeps the shop window clean without
   dropping data.
3. **community-build candidate list** (the original motivation) — a separate
   curated view over `discovered ∩ {Scala 3, macro/inline usage}`. The macro
   signal is **not** in Maven metadata; it needs a cheap repo scan (depends on
   `scala3-library` *and* source contains `inline` / `'{` / `${`). Out of scope
   for Scaladex itself — realistically a human picks from the filtered list.

### Hard filters (small, safe — everything else is "index + rank")

- groupId already in `database.getGroupIds()` → not discovery; `findMissing`
  owns it.
- no resolvable `<scm>` after one POM fetch → `NoGithubRepo`; record and don't
  retry more than once.
- GitHub repo 404 / archived / 0 % Scala.
- `discovered_group_ids.status = rejected` (admin said no) → sticky, never
  re-surfaced; plus a static groupId denylist for known junk namespaces.

Rough effect on the 140: hard filters remove ~15–25, leaving ~115 auto-indexed,
of which ~30–40 would actually surface after the star/claim gate.

---

## References

- `modules/server/src/main/scala/scaladex/server/service/MavenCentralService.scala`
- `modules/infra/src/main/scala/scaladex/infra/MavenCentralClientImpl.scala`
- `modules/core/shared/src/main/scala/scaladex/core/model/Artifact.scala` (`ArtifactId.parse`, `isScala`, `BinaryVersion`)
- `modules/server/src/main/scala/scaladex/server/service/AdminService.scala` (`jobs`)
- Maven Central Index: <https://maven.apache.org/repository/central-index.html>
- `indexer-reader` (dependency-free, no Lucene): <https://maven.apache.org/maven-indexer/indexer-reader/>,
  source `apache/maven-indexer` → `indexer-reader/` (`IndexReader`, `ChunkReader`,
  `RecordExpander`), coordinates `org.apache.maven.indexer:indexer-reader:7.1.6`
- Central Portal publish API (no third-party search): <https://central.sonatype.org/publish/publish-portal-api/>
- Legacy Solr REST guide: <https://central.sonatype.org/search/rest-api-guide/>
- JetBrains Package Search: deprecated Dec 2024, web service + API shut down 1 Apr 2025
  (`JetBrains/package-search-intellij-plugin` readme)
- scala/scala3#26958 (community-build regression motivation)
