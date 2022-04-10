package scaladex.infra.sql

import java.util.UUID

import doobie.Query0
import doobie.util.query.Query
import doobie.util.update.Update
import scaladex.core.model.UserState
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

object UserSessionsTable {

  private[sql] val table = "user_sessions"
  private val userId = "user_id"
  private val userStateFields = Seq("repos", "orgs")
  private val userInfoFields = Seq("login", "name", "avatar_url", "secret")
  private val allFields = userId +: (userStateFields ++ userInfoFields)

  val insertOrUpdate: Update[(UUID, UserState)] =
    insertOrUpdateRequest(table, allFields, Seq(userId))

  val selectUserSessionById: Query[UUID, UserState] =
    selectRequest(table, userStateFields ++ userInfoFields, Seq("user_id"))

  val selectAllUserSessions: Query0[(UUID, UserState)] =
    selectRequest(table, fields = allFields)

  val deleteByUserId: Update[UUID] =
    deleteRequest(table, Seq(userId))
}
