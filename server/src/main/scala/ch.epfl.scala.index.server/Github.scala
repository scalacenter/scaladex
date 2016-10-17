package ch.epfl.scala.index
package server

import model.misc._
import data.github._

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import HttpMethods.POST
import headers._
import Uri._
import unmarshalling.{Unmarshal, Unmarshaller}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Future

import com.typesafe.config.ConfigFactory

object Response {
  case class Permissions(admin: Boolean, push: Boolean, pull: Boolean)
  case class AccessToken(access_token: String)
  case class Repo(full_name: String, permissions: Permissions)
  case class User(login: String, name: Option[String], avatar_url: String)
  case class Organization(login: String)
}

case class UserState(repos: Set[GithubRepo], orgs: Set[Response.Organization], user: UserInfo) {
  def isAdmin = repos.exists {
    case GithubRepo(organization, repository) =>
      organization == "scalacenter" &&
        repository == "scaladex"
  }
  def isSonatype = orgs.contains(Response.Organization("sonatype"))
}

class Github(implicit system: ActorSystem, materializer: ActorMaterializer) extends Json4sSupport {
  import system.dispatcher

  val config       = ConfigFactory.load().getConfig("org.scala_lang.index.server.oauth2")
  val clientId     = config.getString("client-id")
  val clientSecret = config.getString("client-secret")
  val redirectUri  = config.getString("uri") + "/callback/done"

  def getUserStateWithToken(token: String): Future[UserState] = info(token)
  def getUserStateWithOauth2(code: String): Future[UserState] = {
    def access = {
      Http()
        .singleRequest(
            HttpRequest(
                method = POST,
                uri = Uri("https://github.com/login/oauth/access_token").withQuery(
                    Query(
                        "client_id"     -> clientId,
                        "client_secret" -> clientSecret,
                        "code"          -> code,
                        "redirect_uri"  -> redirectUri
                    )),
                headers = List(Accept(MediaTypes.`application/json`))
            ))
        .flatMap(response => Unmarshal(response).to[Response.AccessToken].map(_.access_token))
    }

    access.flatMap(info)
  }

  private def info(token: String) = {
    def fetchGithub(path: Path, query: Query = Query.Empty) = {
      Http().singleRequest(
          HttpRequest(
              uri = Uri(s"https://api.github.com").withPath(path).withQuery(query),
              headers = List(Authorization(GenericHttpCredentials("token", token)))
          ))
    }

    def fetchUserRepos(): Future[List[Response.Repo]] = {
      paginated[Response.Repo](Path.Empty / "user" / "repos")
    }

    def fetchOrgs(): Future[List[Response.Organization]] = {
      paginated[Response.Organization](Path.Empty / "user" / "orgs")
    }

    def fetchOrgRepos(org: String): Future[List[Response.Repo]] = {
      paginated[Response.Repo](Path.Empty / "orgs" / org / "repos")
    }

    def paginated[T](path: Path)(implicit ev: Unmarshaller[HttpResponse, List[T]]): Future[List[T]] = {
      def request(page: Option[Int] = None) = {
        val query = page
          .map(p => Query("page" -> p.toString()))
          .getOrElse(Query())

        fetchGithub(path, query)
      }
      request(None).flatMap { r1 =>
        val lastPage = r1.headers.find(_.name == "Link").map(h => extractLastPage(h.value)).getOrElse(1)
        Unmarshal(r1).to[List[T]].map(vs => (vs, lastPage))
      }.flatMap {
        case (vs, lastPage) =>
          val nextPagesRequests = if (lastPage > 1) {
            Future
              .sequence(
                  (2 to lastPage).map(page => request(Some(page)).flatMap(r2 => Unmarshal(r2).to[List[T]])))
              .map(_.flatten)
          } else Future.successful(Nil)

          nextPagesRequests.map(vs2 => vs ++ vs2)
      }
    }

    def fetchUser() =
      fetchGithub(Path.Empty / "user").flatMap(response => Unmarshal(response).to[Response.User])

    for {
      ((repos, user), orgs) <- fetchUserRepos().zip(fetchUser()).zip(fetchOrgs())
      orgRepos <- Future.sequence(orgs.map(org => fetchOrgRepos(org.login)))
    } yield {

      val allRepos = repos ::: orgRepos.flatten

      val githubRepos = allRepos
        .filter(repo => repo.permissions.push || repo.permissions.admin)
        .map { r =>
          val List(owner, repo) = r.full_name.split("/").toList
          GithubRepo(owner.toLowerCase, repo.toLowerCase)
        }.toSet

      val Response.User(login, name, avatarUrl) = user

      UserState(githubRepos, orgs.toSet, UserInfo(login, name, avatarUrl))
    }
  }
}
