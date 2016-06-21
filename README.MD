# Scaladex

[![Travis](https://img.shields.io/travis/scalacenter/scaladex.svg?style=flat-square)](https://travis-ci.org/scalacenter/scaladex)
[![Waffle.io](https://img.shields.io/waffle/label/scalacenter/scaladex/in%20progress.svg?style=flat-square)](https://waffle.io/scalacenter/scaladex)
[![Gitter](https://img.shields.io/gitter/room/scalacenter/scaladex.svg?style=flat-square)](https://gitter.im/scalacenter/scaladex)

## How to contribute:

* adding keywords: submit a PR to [scaladex-metadata](https://github.com/scalacenter/scaladex-metadata)
* claiming you project: submit a PR to [scaladex-data](https://github.com/scalacenter/scaladex-data)
* improving the index: join the [gitter.im channel](https://gitter.im/scalacenter/scaladex)

## How to run:

```bash
$ git submodule init
$ git submodule update
$ sbt
> data/reStart elastic
> project server
> ~re-start
open http://localhost:8080
```

## Project Structure:

```
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

* `sbt universal:packageBin`