package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.newModel.NewProject._
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildInsertOrUpdate
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildSelect
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import ch.epfl.scala.utils.DoobieUtils.Mappings.dependencyWriter
import doobie.Update
import doobie.implicits._
import doobie.util.fragment.Fragment

object ProjectDependenciesTable {
  private val _ =
    dependencyWriter // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "project_dependencies"
  private[sql] val fields = Seq(
    "source_organization",
    "source_repository",
    "target_organization",
    "target_repository"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(
      orgS: Organization,
      repoS: Repository,
      orgT: Organization,
      repoT: Repository
  ): Fragment =
    fr0"$orgS, $repoS, $orgT, $repoT"

  private[tables] def insertOrUpdate(
      elm: (Organization, Repository, Organization, Repository)
  ): doobie.Update0 = {
    val (orgS, repoS, orgT, repoT) = elm
    val onConflict =
      fr0"source_organization, source_repository, target_organization, target_repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      tableFr,
      fieldsFr,
      values(orgS, repoS, orgT, repoT),
      onConflict,
      doAction
    ).update
  }

  def insertMany(
      source: Project.Reference,
      targets: Seq[Project.Reference]
  ): doobie.ConnectionIO[Int] = {
    val elts = targets.map(t => (source.org, source.repo, t.org, t.repo))
    Update[(Organization, Repository, Organization, Repository)](
      insertOrUpdate(elts.head).sql
    ).updateMany(elts)
  }

  def getMostDependentUponProjects(
      max: Int
  ): doobie.Query0[(Organization, Repository, Long)] =
    buildSelect(
      tableFr,
      fr0"source_organization, source_repository, Count(DISTINCT (target_organization, target_repository)) as total",
      fr0"GROUP BY source_organization, source_repository" ++
        fr0" ORDER BY total DESC" ++
        fr0" LIMIT ${max.toLong}"
    ).query[(Organization, Repository, Long)]
}
