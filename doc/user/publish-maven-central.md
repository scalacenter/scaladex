# How to publish on Maven Central

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