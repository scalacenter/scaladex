package scaladex.data
package maven

import scaladex.core.model.Contributor
import scaladex.core.model.Url

private[maven] object PomConvert:
  def apply(model: org.apache.maven.model.Model): ArtifactModel =
    import model.*
    import scala.jdk.CollectionConverters.*
    import scala.util.Try

    def list[T](l: java.util.List[T]): List[T] =
      Option(l).map(_.asScala.toList).getOrElse(List.empty[T])

    def map(l: java.util.Properties): Map[String, String] =
      Option(l).map(_.asScala.toMap).getOrElse(Map())

    def convert(
        contributor: org.apache.maven.model.Contributor
    ): Contributor =
      import contributor.*
      Contributor(
        Option(getName),
        Option(getEmail),
        Option(getUrl),
        Option(getOrganization),
        Option(getOrganizationUrl),
        list(getRoles),
        Option(getTimezone),
        map(getProperties),
        Option(getId)
      )
    end convert

    ArtifactModel(
      getGroupId,
      getArtifactId,
      getVersion,
      getPackaging,
      Option(getName),
      Option(getDescription),
      Try(getInceptionYear).flatMap(y => Try(y.toInt)).toOption,
      Option(getUrl),
      Option(getScm).map { scm =>
        import scm.*
        SourceCodeManagment(
          Option(getConnection),
          Option(getDeveloperConnection),
          Option(getUrl),
          Option(getTag)
        )
      },
      Option(getIssueManagement).map { im =>
        import im.*
        IssueManagement(
          getSystem,
          Option(getUrl)
        )
      },
      list(getMailingLists).map { ml =>
        import ml.*
        MailingList(
          getName,
          Option(getSubscribe),
          Option(getUnsubscribe),
          Option(getPost),
          Option(getArchive),
          list(getOtherArchives)
        )
      },
      list(getContributors).map(convert),
      list(getDevelopers).map(convert),
      list(getLicenses).map { l =>
        import l.*
        License(
          getName,
          Option(getUrl),
          Option(getDistribution),
          Option(getComments)
        )
      },
      list(getDependencies).map { d =>
        import d.*
        Dependency(
          getGroupId,
          getArtifactId,
          getVersion,
          getProperties.asScala.toMap,
          Option(getScope),
          getExclusions.asScala
            .map(e => Exclusion(e.getGroupId, e.getArtifactId))
            .toSet
        )
      },
      list(getRepositories).map { r =>
        import r.*

        def bool(v: String) =
          if v == null then false
          else v.toBoolean

        def convert(policy: org.apache.maven.model.RepositoryPolicy) =
          RepositoryPolicy(
            policy.getChecksumPolicy,
            bool(policy.getEnabled),
            policy.getUpdatePolicy
          )

        Repository(
          getId,
          getLayout,
          getName,
          Option(getUrl),
          Option(getSnapshots).map(convert),
          Option(getReleases).map(convert)
        )
      },
      Option(getOrganization).map { o =>
        import o.*
        Organization(
          getName,
          Option(getUrl)
        )
      }, {
        val properties = getProperties.asScala.toMap
        for
          scalaVersion <- properties.get("scalaVersion")
          sbtVersion <- properties.get("sbtVersion")
        yield SbtPluginTarget(scalaVersion, sbtVersion)
      },
      Option(getProperties).flatMap(_.asScala.toMap.get("info.apiURL")).map(Url.apply),
      Option(getProperties).flatMap(_.asScala.toMap.get("info.versionScheme"))
    )
  end apply
end PomConvert
