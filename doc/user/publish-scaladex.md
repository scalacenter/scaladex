[SBT-POM-Meta]: http://www.scala-sbt.org/1.0/docs/Using-Sonatype.html#Third+-+POM+Metadata

# Sbt Plugin

The fastest way to publish your artifact to Scaladex is using the Scaladex SBT plugin. The publish workflow is
pretty much the same as you might know already. One difference is, that Scaladex plugin will only publish
the POM file. Your packages, docs and sources are not send to Scaladex.
If you don't want to use the plugin, you can just wait until Scaladex reindex itself. 

## Install

Add the following to your sbt project/plugins.sbt file:

```
addSbtPlugin("ch.epfl.scala.index" % "sbt-scaladex" % "0.1.3")
```

Generate a [GitHub personal access token](https://github.com/settings/tokens/new) with the `read:org` scope.


Add the following to your build.sbt file:

```scala
scaladexKeywords in Scaladex := Seq("Foo", "Bar", "Baz")
credentials in Scaladex := Credentials(Path.userHome / ".ivy2" / ".scaladex.credentials")
/*
realm=Scaladex Realm
host=localhost
user=token
password=<github personal access token>
*/

// or 
credentials in Scaladex += Credentials("Scaladex Realm", "localhost", "token", "<github personal access token>")
```

To publish run the following command:

```bash
sbt scaladex:publish
```

### Publish Token

To publish to Scaladex you need to add a GitHub personal acces toekn. Scaladex uses GitHub to authenticate your publish
process and verify that you have permission to the defined repository (SCM Tag)

### Configure the publish process

There are some settings for the Plugin to control the output on Scaladex a bit, like adding keywords, show GitHub info,
show GitHub Readme file, show GitHub contributors.

* **scaladexBaseUri**: This is the main uri to publish to _default_: `https://index.scala-lang.org`
* **scaladexKeywords**: List of keywords for your artifact _default_: `empty`
* **scaladexDownloadReadme**: A flag to download the README from GitHub. _default_: `true`
* **scaladexDownloadInfo**: A flag to download the repository info from GitHub (eg: stars, forks, ...). _default_: `true`
* **scaladexDownloadContributors**: A flag to download the contributors info from GitHub. _default_: `true`
* **[Credentials][SBT-Credentials] (SBT default)**: Configuration for your GitHub credentials to verify write access to the SCM Tag in the Pom file.

**SBT Simple Example**

**Disable Readme, Info, or Contributors**

This might be important for private repositories. With the Plugin we're able to index private repositories
and fetch Contributors, Readme and the Repository Info. If there is critical info configure the access.

```scala
scaladexDownloadReadme in Scaladex := false
scaladexDownloadInfo in Scaladex := false
scaladexDownloadContributors in Scaladex := false
```
### Response codes / Messages

Maybe, you get an error during publishing. This explanation will help you to solve problems you might have.

* **Forbidden** - You don't have push permission to the GitHub repository.
* **NoContent** - The SCM Tag in POM file is Missing. See [SBT Documentation][SBT-POM-Metadata] for how to solve.
* **Unauthorized** - Your login credentials are not right. Check your configuration if you provide 
credentials and also check if they're correct.
* **destination file exists and overwrite == false** - The version exists already on Scaladex. Overriding is
only allowed for Snapshots.

# Http

Requires Basic Authentication to Github (realm: "Scaladex Realm")

```
PUT /publish
  created=2016-11-07T18:26:40.127+01:00
  path=/org/example/foo_2.11/0.8.0/foo_2.11-0.8.0.pom
  readme=[true|false] (default: true)
  contributors=[true|false] (default: true)
  info=[true|false] (default: true)
  keywords=foo (repeated parameter)
  keywords=bar

GET /publish?
  path=/org/example/foo_2.11/0.8.0/foo_2.11-0.8.0.pom

```