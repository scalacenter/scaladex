# How to publish your project

## sbt

### Maven Central

First time:

0. Create [JIRA account](https://issues.sonatype.org/secure/Signup!default.jspa)
1. Install `gpg` e.g. on OSX: `brew install gpg`
2. Run `gpg --gen-key` to generate a new key. Remember the passphrase you used.
3. Publish your public-key you created above e.g. `gpg --keyserver  hkp://pgp.mit.edu --send-keys 433884a7dae3eb82`

For each project:

0. Create [new JIRA issue](https://issues.sonatype.org/secure/CreateIssue.jspa?issuetype=21&pid=10134) using above account to request new repo
1. Wait till [above issue](https://issues.sonatype.org/browse/OSSRH-18266?filter=-2) is resolved
2. Add `sbt-pgp`, `sbt-release` and `sbt-sonatype` as a plugin to your project. Here is an example [plugins.sbt](https://github.com/pathikrit/better-files/blob/master/project/plugins.sbt)
3. Here is an example [build.sbt](https://github.com/pathikrit/better-files/blob/master/build.sbt) that I use for multi-projects 

For each release:

0. `sbt release` (will prompt for passphrase)
1. View artifact here: https://oss.sonatype.org/content/repositories/releases/

source: https://gist.github.com/pathikrit/6a49de2489e53876679b

### Bintray

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