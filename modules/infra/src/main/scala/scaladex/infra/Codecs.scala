package scaladex.infra

import java.time.Instant

import io.circe.*
import io.circe.generic.semiauto.*
import scaladex.core.model.*
import scaladex.core.model.search.GithubInfoDocument
import scaladex.core.util.Secret
import scaladex.infra.github.GithubModel

object Codecs:
  given Codec[Project.Organization] = fromString(_.value, Project.Organization.apply)
  given Codec[Project.Repository] = fromString(_.value, Project.Repository.apply)
  given Codec[Project.Reference] = deriveCodec
  given Codec[Artifact.Name] = fromString(_.value, Artifact.Name.apply)
  given Codec[Instant] = fromLong[Instant](_.toEpochMilli, Instant.ofEpochMilli)

  given Codec[Url] = fromString(_.target, Url.apply)
  given Codec[GithubContributor] = deriveCodec
  given Codec[GithubIssue] = deriveCodec
  given Codec[GithubCommitActivity] = deriveCodec
  given Codec[DocumentationPattern] =
    Codec.from(
      Decoder[Map[String, String]].emap { map =>
        map.toList match
          case (key -> value) :: Nil => Right(DocumentationPattern(key, value))
          case _ => Left(s"Cannot decode json to DocumentationLink: $map")
      },
      Encoder[Map[String, String]].contramap(doc => Map(doc.label -> doc.pattern))
    )
  given Codec[GithubInfoDocument] = deriveCodec
  given Codec[GithubInfo] = deriveCodec

  given Codec[Category] = fromString(_.label, Category.byLabel)
  given Codec[Project.Settings] = deriveCodec
  given Codec[GithubStatus] = deriveCodec
  given Codec[Project] = deriveCodec

  given Codec[Artifact.GroupId] = fromString(_.value, Artifact.GroupId.apply)
  given Codec[Artifact.ArtifactId] = fromString(_.value, Artifact.ArtifactId.apply)
  given Codec[Version] = fromString(_.value, Version.apply)
  given Codec[Platform] = fromString(_.value, Platform.parse(_).get)
  given Codec[Language] = fromString(_.value, Language.parse(_).get)
  given Codec[Resolver] = deriveCodec
  given Codec[License] = fromString(_.shortName, License.allByShortName.apply)
  given given_codec_Artifact_Reference: Codec[Artifact.Reference] = deriveCodec
  given Codec[Artifact] = deriveCodec
  given Codec[ArtifactDependency.Scope] = fromString(_.value, ArtifactDependency.Scope.apply)

  given Codec[ArtifactDependency] = deriveCodec

  given Codec[UserState] = deriveCodec
  given Codec[UserInfo] = deriveCodec
  given Codec[Secret] = fromString(_.decode, Secret.apply)

  given Codec[Contributor] = deriveCodec

  private def fromLong[A](encode: A => Long, decode: Long => A): Codec[A] =
    Codec.from(Decoder[Long].map(decode), Encoder[Long].contramap(encode))

  private def fromString[A](encode: A => String, decode: String => A): Codec[A] =
    Codec.from(Decoder[String].map(decode), Encoder[String].contramap(encode))
end Codecs
