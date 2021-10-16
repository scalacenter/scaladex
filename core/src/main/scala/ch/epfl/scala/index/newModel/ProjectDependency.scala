package ch.epfl.scala.index.newModel

case class ProjectDependency(
    source: NewProject.Reference,
    target: NewProject.Reference
) {}
