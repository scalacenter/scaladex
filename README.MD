# Scaladex

https://waffle.io/scalacenter/scaladex2

How to run:

```bash
$ git submodule init
$ git submodule update
$ sbt
> data/reStart elastic
> webappJVM/reStart
open http://localhost:8080
```

```
Project Structure:

Data Pipeline
  Extract
  * Discover POMs
  * Download POMs
  * Download Parent POMs

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
```