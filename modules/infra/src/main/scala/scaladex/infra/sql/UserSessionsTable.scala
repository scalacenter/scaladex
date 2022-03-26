package scaladex.infra.sql

import java.util.UUID

import doobie.util.query.Query
import doobie.util.update.Update
import scaladex.core.model.UserState
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.insertOrUpdateRequest
import scaladex.infra.sql.DoobieUtils.selectRequest

object UserSessionsTable {

  private[sql] val table = "user_sessions"
  private val userId = "user_id"
  private val userStateFields = Seq("repos", "orgs")
  private val userInfoFields = Seq("login", "name", "avatar_url", "secret")
  private val allFields = userId +: (userStateFields ++ userInfoFields)

  val insertOrUpdate: Update[(UUID, UserState)] =
    insertOrUpdateRequest(table, allFields, allFields)

  val selectUserSessionById: Query[UUID, UserState] =
    selectRequest(table, allFields, Seq("user_id"))
}
