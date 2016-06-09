# Scaladex

https://waffle.io/scalacenter/scaladex2

How to run:

```bash
$ git submodule init
$ git submodule update
$ sbt
> data/reStart elastic
> ~webappJVM/reStart
open http://localhost:8080
```

```
Project Structure:

Data Pipeline
  Extract
  * Bintray
    * Discover POMs
    * Download POMs
    * Download Parent POMs
  * Github
    * README
    * repo info

  Transform
  * Read POMs
  * Convert POMs to a POM Scala Model
  * Cleanup
    * Artifact Name
    * Source Code Repository
    * Licenses
    * Version
  * Convert POM Scala Model to Projects

  Load
  * Index to ElasticSearch

Website
  Server

  Client

Sbt Plugin
```

## Sbt Plugin

The idea behind sbt-scaladex is to notify the scala index of a new libraries being released. The final goal is to merge this plugin directly into sbt.

## Publishing

* install [requirements](http://www.scala-sbt.org/sbt-native-packager/formats/debian.html?highlight=maintainer#requirements) (`fakeroot` and `dpkg`)
* debian:packageBin