package ch.epfl.scala.index.model

case class ScastieProject(
  organization: String,
  repository: String,
  logo: Option[String] = None,
  artifacts: List[String] = Nil
)