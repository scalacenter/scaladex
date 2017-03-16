# How to publish on Bintray

First time:

0. [create a Bintray account](https://bintray.com/signup/index)
1. get your api key (SHA1) at [profile/edit](https://bintray.com/profile/edit) (it look like this: da39a3ee5e6b4b0d3255bfef95601890afd80709)
2. add [bintray-sbt](https://github.com/softprops/bintray-sbt) to a project
3. set your user and api key with `sbt bintrayChangeCredentials`

For each project:

0. add [bintray-sbt](https://github.com/softprops/bintray-sbt) to your project.
1. set a license (ex: `licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.html"))`)

For each release:

0. `sbt publish`

## Sbt Plugins

http://www.scala-sbt.org/release/docs/Bintray-For-Plugins.html