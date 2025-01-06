package scaladex.infra

import java.time.Instant

import io.circe._
import io.circe.generic.semiauto._
import scaladex.core.model._
import scaladex.core.model.search.GithubInfoDocument
import scaladex.core.util.Secret
import scaladex.infra.github.GithubModel

object Codecs {
  implicit val organization: Codec[Project.Organization] = fromString(_.value, Project.Organization.apply)
  implicit val repository: Codec[Project.Repository] = fromString(_.value, Project.Repository.apply)
  implicit val reference: Codec[Project.Reference] = deriveCodec
  implicit val artifactName: Codec[Artifact.Name] = fromString(_.value, Artifact.Name.apply)
  implicit val instant: Codec[Instant] = fromLong[Instant](_.toEpochMilli, Instant.ofEpochMilli)

  implicit val urlCodec: Codec[Url] = fromString(_.target, Url)
  implicit val contributor: Codec[GithubContributor] = deriveCodec
  implicit val githubIssue: Codec[GithubIssue] = deriveCodec
  implicit val githubCommitActivity: Codec[GithubCommitActivity] = deriveCodec
  implicit val documentation: Codec[DocumentationPattern] =
    Codec.from(
      Decoder[Map[String, String]].emap { map =>
        map.toList match {
          case (key -> value) :: Nil => Right(DocumentationPattern(key, value))
          case _                     => Left(s"Cannot decode json to DocumentationLink: $map")
        }
      },
      Encoder[Map[String, String]].contramap(doc => Map(doc.label -> doc.pattern))
    )
  implicit val githubInfoDocumentCodec: Codec[GithubInfoDocument] = deriveCodec
  implicit val githubInfoCodec: Codec[GithubInfo] = deriveCodec

  implicit val categoryCodec: Codec[Category] = fromString(_.label, Category.byLabel)
  implicit val settings: Codec[Project.Settings] = deriveCodec
  implicit val githubStatucCodec: Codec[GithubStatus] = deriveCodec
  implicit val projectCodec: Codec[Project] = deriveCodec

  implicit val groupIdCodec: Codec[Artifact.GroupId] = fromString(_.value, Artifact.GroupId.apply)
  implicit val artifactIdCodec: Codec[Artifact.ArtifactId] = fromString(_.value, Artifact.ArtifactId.apply)
  implicit val semanticVersionCodec: Codec[Version] = fromString(_.value, Version.apply)
  implicit val platformCodec: Codec[Platform] = fromString(_.value, Platform.parse(_).get)
  implicit val languageCodec: Codec[Language] = fromString(_.value, Language.parse(_).get)
  implicit val resolverCodec: Codec[Resolver] = deriveCodec
  implicit val licenseCodec: Codec[License] = fromString(_.shortName, License.allByShortName.apply)
  implicit val artifactRefCodec: Codec[Artifact.Reference] = deriveCodec
  implicit val artifactCodec: Codec[Artifact] = deriveCodec
  implicit val scopeCodec: Codec[ArtifactDependency.Scope] = fromString(_.value, ArtifactDependency.Scope.apply)

  implicit val dependenciesCodec: Codec[ArtifactDependency] = deriveCodec

  implicit val userStateCodec: Codec[UserState] = deriveCodec
  implicit val userInfoCodec: Codec[GithubModel.UserInfo] = deriveCodec
  implicit val coreUserInfoCodec: Codec[UserInfo] = deriveCodec
  implicit val secretCodec: Codec[Secret] = fromString(_.decode, Secret.apply)

  implicit val developerCodec: Codec[Contributor] = deriveCodec

  private def fromLong[A](encode: A => Long, decode: Long => A): Codec[A] =
    Codec.from(Decoder[Long].map(decode), Encoder[Long].contramap(encode))

  private def fromString[A](encode: A => String, decode: String => A): Codec[A] =
    Codec.from(Decoder[String].map(decode), Encoder[String].contramap(encode))

}
