package ch.epfl.scala.index
package server

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.model.misc._
import com.typesafe.config.ConfigFactory
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.Future

object Response {
  case class AccessToken(access_token: String)

  case class PageInfo(endCursor: String, hasNextPage: Boolean)
  case class Repo(nameWithOwner: String, viewerPermission: String)
  case class AllRepos(pageInfo: PageInfo, totalCount: Int, nodes: List[Repo])

  case class Organization(login: String)

  case class User(login: String, name: Option[String], avatarUrl: String) {
    def convert(token: String): UserInfo =
      UserInfo(login, name, avatarUrl, token)
  }
}

case class UserState(repos: Set[GithubRepo],
                     orgs: Set[Response.Organization],
                     user: UserInfo) {
  def isAdmin = orgs.contains(Response.Organization("scalacenter"))
  def isSonatype =
    orgs.contains(Response.Organization("sonatype")) || user.login == "central-ossrh"
  def hasPublishingAuthority = isAdmin || isSonatype
}

class Github(implicit system: ActorSystem, materializer: ActorMaterializer)
    extends Json4sSupport {
  import system.dispatcher

  val config =
    ConfigFactory.load().getConfig("org.scala_lang.index.server.oauth2")
  val clientId = config.getString("client-id")
  val clientSecret = config.getString("client-secret")
  val redirectUri = config.getString("uri") + "/callback/done"

  def getUserStateWithToken(token: String): Future[UserState] = info(token)
  def getUserStateWithOauth2(code: String): Future[UserState] = {
    def access = {
      Http()
        .singleRequest(
          HttpRequest(
            method = POST,
            uri = Uri("https://github.com/login/oauth/access_token").withQuery(
              Query(
                "client_id" -> clientId,
                "client_secret" -> clientSecret,
                "code" -> code,
                "redirect_uri" -> redirectUri
              )
            ),
            headers = List(Accept(MediaTypes.`application/json`))
          )
        )
        .flatMap(
          response =>
            Unmarshal(response).to[Response.AccessToken].map(_.access_token)
        )
    }

    access.flatMap(info)
  }

  private def info(token: String): Future[UserState] = {
    def fetchRepos(): Future[Set[GithubRepo]] = {
      def convert(repos: List[Response.Repo]): Set[GithubRepo] = {
        repos.iterator
          .filter(
            repo =>
              repo.viewerPermission == "WRITE" ||
                repo.viewerPermission == "ADMIN"
          )
          .map { repo =>
            val Array(owner, name) = repo.nameWithOwner.split("/")
            GithubRepo(owner, name)
          }
          .toSet
      }
      def loop(cursorStart: Option[String],
               acc: Set[GithubRepo],
               n: Int): Future[Set[GithubRepo]] = {
        val after = cursorStart.map(s => s"""after: "$s", """).getOrElse("")
        val query =
          s"""|query {
              |  viewer {
              |    repositories(first: 100,$after affiliations: [COLLABORATOR, ORGANIZATION_MEMBER, OWNER]) {
              |      pageInfo {
              |        endCursor
              |        hasNextPage
              |      }
              |      totalCount
              |      nodes {
              |        nameWithOwner
              |        viewerPermission
              |      }
              |    }
              |  }
              |}""".stripMargin

        graphqlRequest(query).flatMap(
          response =>
            Unmarshal(response).to[JValue].flatMap { data =>
              val json: JValue = data \ "data" \ "viewer" \ "repositories"
              json match {
                case JNothing => Future.successful(acc)
                case _ =>
                  val repos = json.extract[Response.AllRepos]
                  val res = acc ++ convert(repos.nodes)
                  if (repos.pageInfo.hasNextPage || n < 5) {
                    loop(cursorStart = Some(repos.pageInfo.endCursor),
                         acc = res,
                         n + 1)
                  } else {
                    Future.successful(res)
                  }
              }
          }
        )
      }
      loop(None, Set(), 0)
    }

    def graphqlRequest(query: String): Future[HttpResponse] = {
      val json = JObject("query" -> JString(query))

      val request =
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri("https://api.github.com/graphql"),
          entity =
            HttpEntity(ContentTypes.`application/json`, compact(render(json))),
          headers = List(Authorization(OAuth2BearerToken(token)))
        )

      Http().singleRequest(request)
    }

    def fetchUser(): Future[UserInfo] = {
      val query =
        """|query {
           |  viewer {
           |    avatarUrl
           |    name
           |    login
           |  }
           |}""".stripMargin

      graphqlRequest(query).flatMap(
        response =>
          Unmarshal(response).to[JValue].map { data =>
            val json = data \ "data" \ "viewer"
            json.extract[Response.User].convert(token)
        }
      )
    }

    def fetchOrganizations(): Future[Set[Response.Organization]] = {
      val query =
        """|query {
           |  viewer {
           |    organizations(first: 100) {
           |      nodes {
           |        login
           |        viewerCanAdminister
           |      }
           |    }
           |  }
           |}""".stripMargin

      graphqlRequest(query).flatMap(
        response =>
          Unmarshal(response).to[JValue].map { data =>
            val json = data \ "data" \ "viewer" \ "organizations" \ "nodes"
            json.extract[Set[Response.Organization]]
        }
      )
    }

    fetchOrganizations().zip(fetchUser()).zip(fetchRepos()).map {
      case ((orgs, user), repos) =>
        UserState(repos, orgs, user)
    }
  }
}
