## General Overview

Scaladex is composed of four repositories: one source repository `scaladex`, two persistence repositories, `scaladex-contrib` and `scaladex-index`, and one configuration repository `scaladex-credentials`.

For development, you should prefer using `scaladex-small-index` that is a small subset of the original `scaladex-index` repository.

```
scaladex
├── scaladex
├── scaladex-contrib
├── scaladex-credentials
└── scaladex-index (or scaladex-small-index)
```

## Requirements

* Java 1.8
* the [sbt](https://www.scala-sbt.org/) build tool

## How to run Scaladex locally

First clone the scaladex repositories and then run sbt.

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

Into the sbt shell:

* Populate the database (only once):

```
data/reStart init
```

 * Start the Scaladex server:

```
~server/reStart
```
 
Then, open `localhost:8080` in your browser.

## Scalafmt

Make sure to run `bin/scalafmt` to format your code.

You can install a pre-commit hook with `bin/hooks.sh`.

## Standalone Elasticsearch Server

If you have a standalone elasticsearch server running on port 9200, scaladex will automatically use it.
Otherwise it will try to create an elasticsearch container.
There is currently no way to use a different port for elasticsearch.

## Architecture

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

As shown in the above diagram, Scaladex uses two Git repositories 
([scaladex-contrib.git](https://github.com/scalacenter/scaladex-contrib) and [scaladex-index.git](https://github.com/scalacenter/scaladex-index)) as persistence mechanisms, 
and ElasticSearch to power the full-text search.

The `contrib.git` repository is read-only for the Scaladex application
(users can write on it, on Github).

Data of the `[scaladex-index.git]` repository are written *only* by Scaladex.
This repository contains the POMs and project information added by users.

## Bintray Data Pipeline

If you want to update the data:

The entry point is at [data/Main.scala](/data/src/main/scala/ch.epfl.scala.index.data/Main.scala)

### List Poms

This step searches on Bintray for artifact containing `_2.10`, `_2.11`, `_2.12`, ...
Bintray contains jcenter, it's a mirror of maven central.

You will need a premium Bintray account.

```
set javaOptions in reStart ++= Seq(
  "-DBINTRAY_USER=XXXXXXX", 
  "-DBINTRAY_PASSWORD=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
)

data/reStart list
```

### Download ivy.xml of SBT plugins from Bintray

This step also requires a premium Bintray.

```
data/reStart sbt
```

### Download Poms

This steps download poms and parent poms from Bintray

```
data/reStart download
data/reStart parent
```

### GitHub

This step downloads GitHub metadata and content.

You need to create a token on your github account: https://github.com/settings/tokens/new

Then fill the following file `../scaladex-dev-credentials/application.conf` with:

```
org.scala_lang.index {
  data {
    github = ["XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"]
  }
}
```

And run

```
data/reStart github
```

## How to deploy

There are two deployment environments, a staging and a production one.

The urls of each environment are:
 * https://index-dev.scala-lang.org/ (staging)
 * https://index.scala-lang.org/

### Requirements

To deploy the application to the server (index.scala-lang.org) you will need to have the following ssh access:

* devscaladex@index.scala-lang.org (staging)
* scaladex@index.scala-lang.org

These people have access:

* [@MasseGuillaume](https://github.com/MasseGuillaume)
* [@heathermiller](https://github.com/heathermiller)
* [@julienrf](https://github.com/julienrf)
* [@jvican](https://github.com/jvican)
* [@olafurpg](https://github.com/olafurpg)
* [@adpi2](https://github.com/adpi2)

### Staging Deployment

* Deploy the index and the server from your workstation

``` bash
sbt deployDevIndex
sbt deployDevServer
```

* Restart the server

```bash
ssh devscaladex@index.scala-lang.org
./server.sh
tail -n 100 -f server.log
```

If all goes well, the [staging scaladex website](https://index-dev.scala-lang.org/) should be up and running.

### Production Deployment

* Similarly you can deploy the production index and server

``` bash
sbt deployIndex
sbt deployServer
```

* And restart the server

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

