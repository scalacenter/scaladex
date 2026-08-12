# Scaladex load tests (Gatling)

Load/performance harness for finding web-UI bottlenecks. It drives realistic traffic at a locally
running Scaladex and produces a Gatling HTML report (percentiles, throughput, errors per request)
showing **which endpoints are slow**.

## 1. Run the app

The app auto-starts Postgres and Elasticsearch containers via testcontainers.
Load the bundled `small-index` and start the server:

```bash
sbt "data/run init"     # populate the DB from small-index (one-time)
sbt server/run          # starts on :8080
```

Wait until the log shows the server bound on `:8080` **and** the `sync-search` job has finished
(front/search pages read from Elasticsearch).

## 2. Run the simulations

Run all simulations:

```bash
sbt loadtest/Gatling/test
```

Or run a single simulation:

```bash
sbt "loadtest/Gatling/testOnly scaladex.loadtest.BrowsingSimulation"
```

The available simulations are:

- `BrowsingSimulation` — sustained realistic browsing mix + SLA assertions (p95 < 1s, >99% success)
- `MixedStressSimulation` — ramp-to-failure to find the knee / capacity
- `SearchStressSimulation` — ramp-to-failure, Elasticsearch endpoints only
- `ProjectPageStressSimulation` — ramp-to-failure, Postgres endpoints only

Tunables (system properties, e.g. `-Dloadtest.maxRate=200`) — the injection profiles live in `Stress`:

| Property | Used by | Default | Meaning |
|----------|---------|---------|---------|
| `loadtest.baseUrl` | all | `http://localhost:8080` | target host (e.g. point at staging) |
| `loadtest.maxRate` | all except `BrowsingSimulation` | `100` | requests/sec at the top of the ramp (and the constant rate) |
| `loadtest.rampDuration` | all | `100` | seconds of the ramp-up phase |
| `loadtest.constantDuration` | all except `MixedStressSimulation` | `100` | seconds of the constant phase held after the ramp |

`BrowsingSimulation` injects at a fixed user rate (its sessions fire several requests each), so
`maxRate` does not apply to it; `rampDuration` and `constantDuration` still do.

Example: `sbt -Dloadtest.maxRate=300 -Dloadtest.rampDuration=120 "loadtest/Gatling/testOnly scaladex.loadtest.MixedStressSimulation"`

## Generating feeders

Feeders make every request target data that actually exists (no skewing from 404s). They are
generated from `small-index` and **committed**, so you normally don't need to regenerate them.
Regenerate only after changing the dataset:

```bash
sbt "data/run generateFeeders"
```
