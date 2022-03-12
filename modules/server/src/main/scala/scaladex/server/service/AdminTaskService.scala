package scaladex.server.service

import scala.collection.mutable
import scala.concurrent.ExecutionContext

import scaladex.core.model.Artifact
import scaladex.view.AdminTaskStatus

class AdminTaskService(sonatypeSynchronizer: SonatypeSynchronizer)(implicit ec: ExecutionContext) {

  private val adminTasks = mutable.ArrayBuffer[AdminTask]()

  def indexArtifacts(groupId: Artifact.GroupId, artifactNameOpt: Option[Artifact.Name], user: String): Unit = {
    val adminTask = AdminTask("Index artifacts", user, () => sonatypeSynchronizer.syncOne(groupId, artifactNameOpt))
    adminTask.start
    adminTasks += adminTask
  }

  def getAllAdminTasks(): Seq[AdminTaskStatus] = adminTasks.map(_.getStatus).toSeq
}
