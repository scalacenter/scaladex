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

Project Structure:

```
contrib/ (git submodule)
  find missing data & cleanup
data/
  bintray/
    program to fetch data from Bintray
  cleanup/
    use contrib to find missing data & parse stuff
  elastic/
    index artifacts into elasticsearch
  github/
    fetch readme
  maven/
    use maven pom builder to read poms from disk
doc/
  api info
index/
  bintray/
    poms_parent/
      parent pom for inheritence
    poms_sha/
      all scala poms on Bintray
    bintray.json
      bintray search result for "_2.11*.pom"
model/
  artifact & license definitions
webapp/
  jvm/
    server code
  js/
    scalajs code
  shared/
    autowire api
```