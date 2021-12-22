package ch.epfl.scala.utils

import java.time.Instant

import ch.epfl.scala.index.model.License
import ch.epfl.scala.index.model.misc.GithubContributor
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.index.newModel.Artifact
import ch.epfl.scala.index.newModel.Project
import io.circe._
import io.circe.generic.semiauto.deriveCodec

object Codecs {
  implicit val license: Codec[License] = deriveCodec[License]
  implicit val url: Codec[Url] = fromString(_.target, Url(_))

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

  implicit val contributor: Codec[GithubContributor] = deriveCodec[GithubContributor]
  implicit val githubIssue: Codec[GithubIssue] = deriveCodec[GithubIssue]
  implicit val organization: Codec[Project.Organization] = fromString(_.value, Project.Organization.apply)
  implicit val repository: Codec[Project.Repository] = fromString(_.value, Project.Repository.apply)
  implicit val reference: Codec[Project.Reference] = deriveCodec
  implicit val artifactName: Codec[Artifact.Name] = fromString(_.value, Artifact.Name.apply)
  implicit val instant: Codec[Instant] = fromLong[Instant](_.toEpochMilli, Instant.ofEpochMilli)
  implicit val githubInfo: Codec[GithubInfo] = deriveCodec[GithubInfo]
  implicit val githubStatus: Codec[GithubStatus] = deriveCodec[GithubStatus]
  implicit val dataForm: Codec[Project.DataForm] = deriveCodec[Project.DataForm]

  private def fromLong[A](encode: A => Long, decode: Long => A): Codec[A] =
    Codec.from(Decoder[Long].map(decode), Encoder[Long].contramap(encode))

  private def fromString[A](encode: A => String, decode: String => A): Codec[A] =
    Codec.from(Decoder[String].map(decode), Encoder[String].contramap(encode))
}
