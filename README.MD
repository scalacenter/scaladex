[SBT-Credentials]: http://www.scala-sbt.org/0.13/docs/Publishing.html#Credentials
[SBT-POM-Meta]: http://www.scala-sbt.org/1.0/docs/Using-Sonatype.html#Third+-+POM+Metadata
[gitter.im channel]: https://gitter.im/scalacenter/scaladex
[scaladex-metadata]: https://github.com/scalacenter/scaladex-metadata
[scaladex-data]: https://github.com/scalacenter/scaladex-data
[Scaladex SBT Plugin]: #how-to-publish-on-scaladex
[GitHub]: https://github.com
# Scaladex

[![Travis](https://img.shields.io/travis/scalacenter/scaladex.svg?style=flat-square)](https://travis-ci.org/scalacenter/scaladex)
[![Waffle.io](https://img.shields.io/waffle/label/scalacenter/scaladex/in%20progress.svg?style=flat-square)](https://waffle.io/scalacenter/scaladex)
[![Gitter](https://img.shields.io/gitter/room/scalacenter/scaladex.svg?style=flat-square)](https://gitter.im/scalacenter/scaladex)

## How to contribute:

* adding keywords: submit a PR to [scaladex-metadata] or use the [Scaladex SBT Plugin]
* claiming you project: submit a PR to [scaladex-data] or use the [Scaladex SBT Plugin]
* improving the index: join the [gitter.im channel]

## How to publish on scaladex

The fastest way to publish your artifact to Scaladex is using the Scaladex SBT plugin. The publish workflow is
pretty much the same as you might know already. One difference is, that Scaladex plugin will only publish
the POM file. Your packages, docs and sources are not send to Scaladex.  If you don't want to use the plugin, you can
just wait until Scaladex reindex itself. This process could maybe take some days so please be patient.

For Scaladex is necessary to have a SCM tag in the Pom file. SCM means source control management. It's also required at the moment that you have your source code published on [GitHub].
To add an SCM tag to your project you can follow the guidelines of SBT.
[SBT Documentation - POM Metadata][SBT-POM-Meta]

### Install the plugin

To get the Scaladex plugin, you only need to add `addSbtPlugin("ch.epfl.scala.index" % "sbt-scaladex" % "0.1.3")` to
your `plugin.sbt` file.

### Publish Credentials

To publish to Scaladex you need to add some user authentication. Scaladex uses [GitHub] to authenticate your publish
process and verify that you have push permission to the defined repository (SCM Tag). You can see
[SBT Credentials documentation][SBT-Credentials] for how to add the login credentials.

### Configure the publish process

There are some settings for the Plugin to control the output on Scaladex a bit, like adding keywords, show [GitHub] info,
show [GitHub] Readme file, show [GitHub] contributors.

* **scaladexBaseUrl**: This is the main url to publish to _default_: `https://index.scala-lang.org`
* **scaladexKeywords**: List of keywords for your artifact _default_: `empty`
* **scaladexDownloadReadme**: A flag to download the README from [GitHub]. _default_: `true`
* **scaladexDownloadInfo**: A flag to download the repository info from [GitHub] (eg: stars, forks, ...). _default_: `true`
* **scaladexDownloadContributors**: A flag to download the contributors info from [GitHub]. _default_: `true`
* **[Credentials][SBT-Credentials] (SBT default)**: Configuration for your [GitHub] credentials to verify write access to the SCM Tag in the Pom file.

**SBT Simple Example**
```scala
scaladexKeywords in Scaladex := Seq("Foo", "Bar", "Baz")
Credentials in Scaladex := Credentials(Path.userHome / ".ivy2" / ".scaladex.credentials")
```

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

* **Forbidden** - You don't have push permission to the [GitHub] repository.
* **NoContent** - The SCM Tag in POM file is Missing. See [SBT Documentation][SBT-POM-Metadata] for how to solve.
* **Unauthorized** - Your login credentials are not right. Check your configuration if you provide 
credentials and also check if they're correct.
* **destination file exists and overwrite == false** - The version exists already on Scaladex. Overriding is
only allowed for Snapshots.

### Run Publish process

The command to publish your artifact to Scaladex is straight forward. If you have the configuration done
(don't forget to call `reload`) you can simply trigger the SBT-task `scaladex:publish`. If you have trouble
doing this, have a look at the **Response codes / Messages** section. 

```bash
sbt scaladex:publish
```

## How to run Scaladex:

```bash
$ git submodule init
$ git submodule update
$ sbt
> data/reStart elastic # do only once to populate the indices
> project scaladexJVM # this is the server
> ~re-start
```
  
Then, open `localhost:8080` in your browser.

## How to publish the Scaladex-Plugin

``` 
$ sbt
> project sbtScaladex
> bintrayChangeCredentials
# username: scaladex
# api key: **********
```

## Requirements

To run the application the css compiler `sass` is required. If you don't have it follow the instructions on [sass install page](http://sass-lang.com/install).

## Sbt Plugin

The idea behind sbt-scaladex is to notify the scala index of a new libraries being released. The final goal is to merge this plugin directly into sbt.

## Production Mode

```bash
sbt server/universal:packageBin
git clone git@github.com:scalacenter/scaladex.git
nohup ~/webapp/bin/server -Dconfig.file=scaladex-credentials/application.conf &
```
