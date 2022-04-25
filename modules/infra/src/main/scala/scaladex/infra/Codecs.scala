package scaladex.infra

import java.time.Instant

import io.circe._
import io.circe.generic.semiauto.deriveCodec
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Category
import scaladex.core.model.DocumentationPattern
import scaladex.core.model.GithubContributor
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubIssue
import scaladex.core.model.GithubStatus
import scaladex.core.model.Language
import scaladex.core.model.License
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.Resolver
import scaladex.core.model.SemanticVersion
import scaladex.core.model.Url
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
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
  implicit val semanticVersionCodec: Codec[SemanticVersion] = fromString(_.encode, SemanticVersion.parse(_).get)
  implicit val platformCodec: Codec[Platform] = fromString(_.label, Platform.fromLabel(_).get)
  implicit val languageCodec: Codec[Language] = fromString(_.label, Language.fromLabel(_).get)
  implicit val resolverCodec: Codec[Resolver] = deriveCodec
  implicit val licenseCodec: Codec[License] = deriveCodec
  implicit val artifactCodec: Codec[Artifact] = deriveCodec
  implicit val scopeCodec: Codec[ArtifactDependency.Scope] = fromString(_.value, ArtifactDependency.Scope.apply)

  implicit val mavenRefCodec: Codec[Artifact.MavenReference] = deriveCodec
  implicit val dependenciesCodec: Codec[ArtifactDependency] = deriveCodec

  implicit val userStateCodec: Codec[UserState] = deriveCodec
  implicit val userInfoCodec: Codec[GithubModel.UserInfo] = deriveCodec
  implicit val coreUserInfoCodec: Codec[UserInfo] = deriveCodec
  implicit val secretCodec: Codec[Secret] = fromString(_.decode, Secret.apply)

  private def fromLong[A](encode: A => Long, decode: Long => A): Codec[A] =
    Codec.from(Decoder[Long].map(decode), Encoder[Long].contramap(encode))

  private def fromString[A](encode: A => String, decode: String => A): Codec[A] =
    Codec.from(Decoder[String].map(decode), Encoder[String].contramap(encode))
}
