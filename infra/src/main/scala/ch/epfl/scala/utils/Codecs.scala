package ch.epfl.scala.utils

import java.time.Instant

import ch.epfl.scala.index.model.License
import ch.epfl.scala.index.model.misc.GithubContributor
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import io.circe._
import io.circe.generic.semiauto.deriveCodec

object Codecs {
  implicit val license: Codec[License] = deriveCodec[License]
  implicit val url: Codec[Url] = fromString(_.target, Url(_))
  implicit val documentation: Codec[NewProject.DocumentationLink] = deriveCodec[NewProject.DocumentationLink]
  implicit val contributor: Codec[GithubContributor] = deriveCodec[GithubContributor]
  implicit val githubIssue: Codec[GithubIssue] = deriveCodec[GithubIssue]
  implicit val organization: Codec[NewProject.Organization] = fromString(_.value, NewProject.Organization.apply)
  implicit val repository: Codec[NewProject.Repository] = fromString(_.value, NewProject.Repository.apply)
  implicit val artifactName: Codec[NewRelease.ArtifactName] = fromString(_.value, NewRelease.ArtifactName.apply)
  implicit val instant: Codec[Instant] = fromLong[Instant](_.toEpochMilli, Instant.ofEpochMilli)
  implicit val githubInfo: Codec[GithubInfo] = deriveCodec[GithubInfo]
  implicit val githubStatus: Codec[GithubStatus] = deriveCodec[GithubStatus]

  private def fromLong[A](encode: A => Long, decode: Long => A): Codec[A] =
    Codec.from(Decoder[Long].map(decode), Encoder[Long].contramap(encode))

  private def fromString[A](encode: A => String, decode: String => A): Codec[A] =
    Codec.from(Decoder[String].map(decode), Encoder[String].contramap(encode))
}
