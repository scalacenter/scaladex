## Requirements

* a jvm
* sbt
* css compiler [`sass`](http://sass-lang.com/install)

## How to run Scaladex locally:

```bash
git clone git@github.com:scalacenter/scaladex.git
git clone git@github.com:scalacenter/scaladex-index.git index
git clone git@github.com:scalacenter/scaladex-contrib.git contrib
cd scaladex
sbt
```

do only once to populate the index

`data/reStart elastic /path/to/contrib /path/to/index`

`~server/reStart 8080 /path/to/contrib /path/to/index``
 
Then, open `localhost:8080` in your browser.

### Elasticsearch Remote

If you have an elasticsearch service installed use the following sbt command when indexing/running the server:

`set javaOptions in reStart += "-DELASTICSEARCH=remote"`

## Bintray Data Pipeline

If you want to update the data:

The entry point is at [BintrayPipeline.scala](/data/src/main/scala/ch.epfl.scala.index.data/bintray/BintrayPipeline.scala)

### List Poms

This step will search on Bintray for artifact containing `_2.10`, `_2.11`, `_2.12`. Bintray contains jcenter,
it's a mirror of maven central.

You will need a premium Bintray account.

```
set javaOptions in reStart ++= Seq("-DBINTRAY_USER=XXXXXXX", "-DBINTRAY_PASSWORD=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
data/reStart list /path/to/contrib /path/to/index`
```

### Download

This step will download poms from Bintray

```
data/reStart download /path/to/contrib /path/to/index`
# wait for task to complete
data/reStart parent /path/to/contrib /path/to/index`
```

### Github

This step will download GitHub metadata and content

```
data/reStart github /path/to/contrib /path/to/index`
```

## How to publish the Scaladex SBT Plugin

``` 
$ sbt
> sbtScaladex/bintrayChangeCredentials
# username: scaladex
# api key: **********
> sbtScaladex/publish
```

## Testing publish

curl --data-binary "@test_2.11-1.1.5.pom" \
-XPUT \
--user token:0672151d424d2bf85331fbec76ab70d937837621 \
"http://localhost:8080/publish?test=true&readme=true&info=true&contributors=true&path=/org/example/test_2.11/1.2.3/test_2.11-1.2.3.pom"

or via `sbt sbtScaladex/scripted`

github test user:

user: foobarbuz 
pass: tLA4FN9O5jmPSnl/LDkSb0cYgJDe8HHdOMkY2yZO4m0=
api:  0672151d424d2bf85331fbec76ab70d937837621
repo: git@github.com:foobarbuz/example.git