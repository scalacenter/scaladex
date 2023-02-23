package scaladex.infra.sql

import java.util.UUID

import doobie.Query0
import doobie.util.query.Query
import doobie.util.update.Update
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

object UserSessionsTable {

  private[sql] val table = "user_sessions"
  private val userId = "user_id"
  private val userInfoFields = Seq("login", "name", "avatar_url", "secret")
  private val userStateFields = Seq("repos", "orgs") ++ userInfoFields

  val insert: Update[(UUID, UserInfo)] =
    insertRequest(table, userId +: userInfoFields)

  val update: Update[(UserState, UUID)] =
    updateRequest(table, userStateFields, Seq(userId))

  val selectById: Query[UUID, UserState] =
    selectRequest(table, userStateFields, Seq("user_id"))

  val selectAll: Query0[(UUID, UserInfo)] =
    selectRequest(table, fields = userId +: userInfoFields)

  val deleteById: Update[UUID] =
    deleteRequest(table, Seq(userId))
}
