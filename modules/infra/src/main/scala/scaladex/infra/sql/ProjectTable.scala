package scaladex.infra.sql

import java.time.Instant

import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.infra.sql.DoobieMappings.given
import scaladex.infra.sql.DoobieUtils.*

import doobie.*
import doobie.util.update.Update

object ProjectTable:
  private val table: String = "projects"

  private val referenceFields = Seq("organization", "repository")
  private val newReferenceFields = Seq("new_organization", "new_repository")
  private val creationDateFields = Seq("creation_date")
  private val githubStatusFields =
    Seq("github_status", "github_update_date") ++ newReferenceFields ++ Seq("error_code", "error_message")

  private val fields: Seq[String] = referenceFields ++ creationDateFields ++ githubStatusFields

  private val allFields: Seq[String] = fields.map("p." + _) ++
    GithubInfoTable.infoFields.map("g." + _) ++
    ProjectSettingsTable.settingsFields.map("f." + _)

  private val fullTable: String =
    s"$table p " +
      s"LEFT JOIN ${GithubInfoTable.table} g ON p.organization = g.organization AND p.repository = g.repository " +
      s"LEFT JOIN ${ProjectSettingsTable.table} f ON p.organization = f.organization AND p.repository = f.repository"

  val insertIfNotExists: Update[(Project.Reference, GithubStatus)] =
    insertOrUpdateRequest(
      table,
      referenceFields ++ githubStatusFields,
      referenceFields
    )

  val updateCreationDate: Update[(Instant, Project.Reference)] =
    updateRequest(table, creationDateFields, referenceFields)

  val updateGithubStatus: Update[(GithubStatus, Project.Reference)] =
    updateRequest(table, githubStatusFields, referenceFields)

  val countProjects: Query0[Long] =
    selectRequest(table, Seq("count(*)"))

  val selectByReference: Query[Project.Reference, Project] =
    selectRequest(fullTable, allFields, referenceFields.map(f => s"p.$f"))

  val selectByNewReference: Query[Project.Reference, Project.Reference] =
    selectRequest(table, referenceFields, newReferenceFields)

  val selectReferenceAndStatus: Query0[(Project.Reference, GithubStatus)] =
    selectRequest(table, referenceFields ++ githubStatusFields)

  val selectProject: Query0[Project] =
    selectRequest(fullTable, allFields)

  val selectProjectByGithubStatus: Query[String, Project.Reference] =
    selectRequest(table, referenceFields, Seq("github_status"))
end ProjectTable
