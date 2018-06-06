## Requirements

* a jvm
* sbt
* css compiler [`sass`](http://sass-lang.com/install)

## How to run Scaladex locally:

```
scaladex
├── scaladex
├── scaladex-contrib
├── scaladex-credentials (optionnal)
└── scaladex-small-index
```

```bash
mkdir scaladex
cd scaladex

git clone git@github.com:scalacenter/scaladex.git
git clone git@github.com:scalacenter/scaladex-small-index.git
git clone git@github.com:scalacenter/scaladex-contrib.git

# Optional: If you have access
git clone git@github.com:scalacenter/scaladex-credentials.git

cd scaladex
sbt
```

do only once to populate the index

`data/reStart elastic`

`~server/reStart`
 
Then, open `localhost:8080` in your browser.

## Scalafmt

Make shure to run `bin/scalafmt` to format your code.

You can intall a pre-commit hook with `bin/hooks.sh`

### Elasticsearch Remote

If you have an elasticsearch service installed use the following sbt command when
indexing/running the server:

`set javaOptions in reStart += "-DELASTICSEARCH=remote"`

## Overview

```
+-----------------------+                                          +-----------------------+
|                       |                                          |                       |
|     Bintray           +----------------------------------------> |  [scaladex-index.git] |
|                       |                                          |                       |
+-----------------------+                                          +-----------------------+

                                                                             ^
                                                        write edit           |
                                                  +--------------------------+
                                                  |
                                                  |
+-----------------------+                         |
|                       |                 +-------+-------+            +-------------------+
|       Sonatype        |   upload poms   |               |  search    |                   |
|                       +---------------> |   Scaladex    +--------->  |   Elasticsearch   |
|  +-----------------+  |                 |      Web      |            |                   |
|  |                 |  |  edit projects  |               |            +-------------------+
|  | Scala Developer +------------------> +-------+-------+
|  |                 |  |                         |
|  +-------------+---+  |                         | read claims   +------------------------+
|                |      |                         +------------>  |                        |
+-----------------------+                                         | [scaladex-contrib.git] |
                 |                 claims projects                |                        |
                 +--------------------------------------------->  +------------------------+

-- asciiflow.com
```

* [scaladex-index.git](https://github.com/scalacenter/scaladex-index)
* [scaladex-contrib.git](https://github.com/scalacenter/scaladex-contrib)

As shown in the above diagram, Scaladex uses two Git repositories 
(`[scaladex-contrib.git]` and `[scaladex-index.git]`) as persistence mechanisms, 
and ElasticSearch to power the full-text search.

The `contrib.git` repository is read-only for the Scaladex application
(users can write on it, on Github).

Data of the `[scaladex-index.git]` repository are written *only* by Scaladex.
This repository contains the POMs and project information added by users.

## Bintray Data Pipeline

If you want to update the data:

The entry point is at [data/Main.scala](/data/src/main/scala/ch.epfl.scala.index.data/Main.scala)

### List Poms / Sbt

This step will search on Bintray for artifact containing `_2.10`, `_2.11`, `_2.12`, ...
Bintray contains jcenter, it's a mirror of maven central.

You will need a premium Bintray account.

```
set javaOptions in reStart ++= Seq(
  "-DBINTRAY_USER=XXXXXXX", 
  "-DBINTRAY_PASSWORD=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
)
data/reStart list
data/reStart download-sbt-plugins
```

### Download

This step will download poms from Bintray

```
data/reStart download`
# wait for task to complete
data/reStart parent`
```

### GitHub

This step will download GitHub metadata and content.

You need a token for this step. https://github.com/settings/tokens/new

Then create the following file `../scaladex-dev-credentials/application.conf`

```
org.scala_lang.index {
  data {
    github = ["XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"]
  }
}
```

And run

`data/reStart github`

## How to deploy

### Requirements

To deploy the application to the production server (index.scala-lang.org) you will need to have ssh access to the following machine:

* ssh devscaladex@index.scala-lang.org (staging)
* ssh scaladex@index.scala-lang.org

These people have access:

* [@MasseGuillaume](https://github.com/MasseGuillaume)
* [@heathermiller](https://github.com/heathermiller)
* [@julienrf](https://github.com/julienrf)
* [@jvican](https://github.com/jvican)
* [@olafurpg](https://github.com/olafurpg)

To deploy the index and the server:

* sbt deployIndex
* sbt deployServer

Or

* sbt deployDevIndex
* sbt deployDevServer

## How to restart the server

```bash
ssh scaladex@index.scala-lang.org
./server.sh
tail -n 100 -f server.log
```

## Testing publish

Requests must be authenticated with Basic HTTP authentication:

- login: `token`
- password: a Github personal access token with `read:org` scope. You can create one
  [here](https://github.com/settings/tokens/new)

~~~
curl --data-binary "@test_2.11-1.1.5.pom" \
-XPUT \
--user token:c61e65b80662c064abe923a407b936894b29fb55 \
"http://localhost:8080/publish?created=1478668532&readme=true&info=true&contributors=true&path=/org/example/test_2.11/1.2.3/test_2.11-1.2.3.pom"
~~~

~~~
curl --data-binary "@noscm_2.11-1.0.0.pom" \
-XPUT \
--user token:c61e65b80662c064abe923a407b936894b29fb55 \
"http://localhost:8080/publish?created=1478668532&readme=true&info=true&contributors=true&path=/org/example/noscm_2.11/1.0.0/noscm_2.11-1.0.0.pom"
~~~

~~~
curl --data-binary "@test_2.11-1.1.5.pom" \
-XPUT \
--user token:c61e65b80662c064abe923a407b936894b29fb55 \
"https://index.scala-lang.org/publish?created=1478668532&readme=true&info=true&contributors=true&path=/org/example/test_2.11/1.2.3/test_2.11-1.2.3.pom"
~~~

github test user:

user: foobarbuz 
pass: tLA4FN9O5jmPSnl/LDkSb0cYgJDe8HHdOMkY2yZO4m0=
api:  c61e65b80662c064abe923a407b936894b29fb55
repo: git@github.com:foobarbuz/example.git

