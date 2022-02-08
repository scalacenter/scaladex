package scaladex.infra

import java.time.Instant

import io.circe._
import io.circe.generic.semiauto.deriveCodec
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.GithubContributor
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubIssue
import scaladex.core.model.GithubStatus
import scaladex.core.model.License
import scaladex.core.model.Project
import scaladex.core.model.Resolver
import scaladex.core.model.SemanticVersion
import scaladex.core.model.Url
import scaladex.core.model.search.GithubInfoDocument

object Codecs {
  implicit val organization: Codec[Project.Organization] = fromString(_.value, Project.Organization.apply)
  implicit val repository: Codec[Project.Repository] = fromString(_.value, Project.Repository.apply)
  implicit val reference: Codec[Project.Reference] = deriveCodec
  implicit val artifactName: Codec[Artifact.Name] = fromString(_.value, Artifact.Name.apply)
  implicit val instant: Codec[Instant] = fromLong[Instant](_.toEpochMilli, Instant.ofEpochMilli)

  implicit val urlCodec: Codec[Url] = fromString(_.target, Url)
  implicit val contributor: Codec[GithubContributor] = deriveCodec
  implicit val githubIssue: Codec[GithubIssue] = deriveCodec
  implicit val documentation: Codec[Project.DocumentationLink] =
    Codec.from(
      Decoder[Map[String, String]].emap { map =>
        map.toList match {
          case (key -> value) :: Nil => Right(Project.DocumentationLink(key, value))
          case _                     => Left(s"Cannot decode json to DocumentationLink: $map")
        }
      },
      Encoder[Map[String, String]].contramap(docLink => Map(docLink.label -> docLink.link))
    )
  implicit val githubInfoDocumentCodec: Codec[GithubInfoDocument] = deriveCodec
  implicit val githubInfoCodec: Codec[GithubInfo] = deriveCodec

  implicit val categoryCodec: Codec[Category] = fromString(_.label, Category.byLabel)
  implicit val settings: Codec[Project.Settings] = deriveCodec
  implicit val githubStatucCodec: Codec[GithubStatus] = deriveCodec
  implicit val projectCodec: Codec[Project] = deriveCodec

  implicit val groupIdCodec: Codec[Artifact.GroupId] = fromString(_.value, Artifact.GroupId.apply)
  implicit val semanticVersionCodec: Codec[SemanticVersion] = fromString(_.encode, SemanticVersion.parse(_).get)
  implicit val binaryVersionCodec: Codec[BinaryVersion] = fromString(_.encode, BinaryVersion.parse(_).get)
  implicit val resolverCodec: Codec[Resolver] = deriveCodec
  implicit val licenseCodec: Codec[License] = deriveCodec
  implicit val artifactCodec: Codec[Artifact] = deriveCodec

  implicit val mavenRefCodec: Codec[Artifact.MavenReference] = deriveCodec
  implicit val dependenciesCodec: Codec[ArtifactDependency] = deriveCodec

  private def fromLong[A](encode: A => Long, decode: Long => A): Codec[A] =
    Codec.from(Decoder[Long].map(decode), Encoder[Long].contramap(encode))

  private def fromString[A](encode: A => String, decode: String => A): Codec[A] =
    Codec.from(Decoder[String].map(decode), Encoder[String].contramap(encode))
}
