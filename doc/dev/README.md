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
> set javaOptions in reStart := Seq("-DELASTICSEARCH=remote", "-Xmx2g")
> ~server/reStart
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

## How to deploy Scaladex

* request access to alaska.epfl.ch and github.com/scalacenter/scaladex-credentials

```bash
sbt server/universal:packageBin
scp server/target/universal/server-0.1.3.zip scaladex@alaska.epfl.ch:app.zip
ssh scaladex@alaska.epfl.ch

# first time
# git clone git@github.com:scalacenter/scaladex.git
# git clone git@github.com:scalacenter/scaladex-credentials.git
# cd ~/scaladex
# git submodule init
# git submodule update

# Kill server
jps
# kill -9 (Server pid)
rm -rf ~/.esdata/data/
rm -rf server-*/
cd ~/scaladex
sbt data/reStart elastic

unzip app.zip

nohup ~/webapp/bin/server -Dconfig.file=scaladex-credentials/application.conf &
```