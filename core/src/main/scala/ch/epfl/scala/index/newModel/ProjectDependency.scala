package ch.epfl.scala.index.newModel

case class ProjectDependency(
    source: Project.Reference,
    target: Project.Reference
)
