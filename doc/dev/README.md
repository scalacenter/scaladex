## Requirements

* a jvm
* sbt
* css compiler [`sass`](http://sass-lang.com/install)

## How to run Scaladex locally:

All the data is store under version control via git submodules. You only need to run the following to get a server
locally:

```bash
$ git submodule init
$ git submodule update
$ sbt
> data/reStart elastic # do only once to populate the index
> ~server/reStart
```

Then, open `localhost:8080` in your browser.

### Elasticsearch Remote

If you have an elasticsearch service installed use:

```
$ sbt
> set javaOptions in reStart := Seq("-DELASTICSEARCH=remote", "-Xmx3g")
```

## Data Pipeline

If you want to update the data:

The entry point is at [Main.scala](/data/src/main/scala/ch.epfl.scala.index.data/Main.scala)

### List Poms

This step will search on Bintray for artifact containing `_2.10`, `_2.11`, `_2.12`. Bintray contains jcenter,
it's a mirror of maven central.

You will need a premium Bintray account.

```
$ cat ~/.bintray/.credentials2
realm = Bintray API Realm
host = api.bintray.com
user = _username_
password = _apikey_
```

```
$ sbt
> project data
> reStart list
```

Will generate: `index/bintray/bintray.json`

### Download

This step will download poms from Bintray

```
$ sbt
> project data
> reStart download
# wait for task to complete
> reStart parent
```

Generates: `index/bintray/pom_sha/*` and `index/bintray/pom_parent/*`

### Github

This step will download GitHub metadata and content

```
$ sbt
> project data
> reStart github
```

Generates: `index/github/*`

## How to publish the Scaladex SBT Plugin

``` 
$ sbt
> project sbtScaladex
> bintrayChangeCredentials
# username: scaladex
# api key: **********
> publish
```

## Testing publish

curl -d="@n_2.11-1.1.5.pom" \
-XPUT \
--user token:XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
"http://localhost:8080/publish?test=true&readme=true&info=true&contributors=true&path=/org/example/test_2.11/1.2.3/test_2.11-1.2.3.pom"

or via `sbt sbtScaladex/scripted`