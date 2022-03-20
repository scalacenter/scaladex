package scaladex.infra.sql

import java.util.UUID

import doobie.util.update.Update
import scaladex.core.model.UserState
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.insertOrUpdateRequest

object UserSessionsTable {

  private[sql] val table = "user_sessions"

  private val userId = "user_id"
  private val userSessionFields = Seq("repos", "orgs")
  private val userInfoFields = Seq("login", "name", "avatar_url", "secret")
  private val allFields = userId +: (userSessionFields ++ userInfoFields)

  val insertOrUpdate: Update[(UUID, UserState)] =
    insertOrUpdateRequest(table, allFields, allFields)
}
