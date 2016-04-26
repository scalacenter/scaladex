package ch.epfl.scala.index
package maven

object PomConvert {
  def apply(model: org.apache.maven.model.Model): MavenModel = {
    import model._
    import scala.collection.JavaConverters._

    def convert(contributor: org.apache.maven.model.Contributor): Contributor = {
      import contributor._
      Contributor(
        Option(getName),
        Option(getEmail),
        Option(getUrl),
        Option(getOrganization),
        Option(getOrganizationUrl),
        getRoles.asScala.toList,
        Option(getTimezone),
        getProperties.asScala.toMap,
        Option(getId)
      )
    }

    def list[T](l: java.util.List[T]): List[T] =
      Option(l).map(_.asScala.toList).getOrElse(List.empty[T])
    
    MavenModel(
      getGroupId,
      getArtifactId,
      getVersion,
      getPackaging,
      getName,
      Option(getUrl),
      Option(getScm).map{scm =>
        import scm._
        SourceCodeManagment(
          Option(getConnection),
          Option(getDeveloperConnection),
          Option(getUrl),
          Option(getTag)
        )
      },
      Option(getIssueManagement).map{ im =>
        import im._
        IssueManagement(
          getSystem,
          Option(getUrl)
        )
      },
      list(getMailingLists).map{ ml =>
        import ml._
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
      list(getLicenses).map{ l =>
        import l._
        License(
          getName,
          Option(getUrl),
          Option(getDistribution),
          Option(getComments)
        )
      },
      list(getDependencies).map{ d =>
        import d._
        Dependency(
          getGroupId,
          getArtifactId,
          getVersion,
          getProperties.asScala.toMap,
          Option(getScope),
          getExclusions.asScala.map( e =>
            Exclusion(e.getGroupId, e.getArtifactId)
          ).toSet
        )
      },
      list(getRepositories).map{ r =>
        import r._
        
        def bool(v: String) =
          if(v == null) false
          else v.toBoolean

        def convert(policy: org.apache.maven.model.RepositoryPolicy) = RepositoryPolicy(
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
      Option(getOrganization).map{ o =>
        import o._
        Organization(
          getName,
          Option(getUrl)
        )
      }
    )
  }
}