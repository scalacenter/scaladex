package ch.epfl.scala.index
package components

import autowire._
import rpc._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import css._

import japgolly.scalajs.react._, vdom.all._

import scalacss.Defaults._
import scalacss.ScalaCssReact._

object ProjectView {
  object Style extends StyleSheet.Inline {
    import dsl._

    val container = style(
      marginLeft(10.px),
      marginRight(10.px)
    )

    val readme = style(
      width(70.%%),
      display.inlineBlock
    )

    val side = style(
      width(30.%%),
      display.inlineBlock,
      overflow.hidden,
      verticalAlign.top
    )

    val abc = style(
      margin(5.px)
    )
  }

  private val ProjectSearch = ReactComponentB[(String, Backend)]("ProjectSearch")
  private val ProjectSideBar = ReactComponentB[Project]("ProjectSideBar")
    .render_P ( project =>
      project.releases.headOption match {
        case Some(last) => {
          val name = last.name.getOrElse("")
          val description = last.description.getOrElse("")

          import last.ref._

          def deps(dependencies: Set[ArtifactRef]) =
            ul(Style.abc)(dependencies.map(dep => (dep.groupId, dep.artifactId)).map{ case (gid, aid) =>
              li(a(href := s"/projects/$gid/$aid", target := "_blank")(
                s"$gid:$aid")
              )
            })

          div(
            div(Style.abc)(last.sbtInstall),
            last.scalaDocURI match {
              case Some(uri) => a(Style.abc, href := uri, target := "blank")("scaladoc")
              case None => div(Style.abc)("no scaladoc")
            },
            div(Style.abc)(last.github.map{ case GithubRepo(user, repo) =>
              a(href := s"https://github.com/$user/$repo")(s"$user/$repo")
            }),
            div(Style.abc)("reverse dependecies"),
            deps(last.reverseDependencies),
            div(Style.abc)("dependecies"),
            deps(last.dependencies)

          )
        }
        case None => div("no releases")
      }
    )
    .build

  private class Backend($: BackendScope[Unit, Option[(Project, Option[String])]]) {
    def render(a: Option[(Project, Option[String])]) = {
      a match {
        case Some((project, Some(markdown))) => 
          div(Style.container)(
            div(Style.readme, dangerouslySetInnerHtml(markdown)),
            div(Style.side)(ProjectSideBar(project))
          )

        case Some((project, None)) => div("no readme")
        case None => div("not found")
      }
    }
  }

  private def View(page: ProjectPage) = 
    ReactComponentB[Unit]("Project View")
    .initialState(None: Option[(Project, Option[String])])
    .renderBackend[Backend]
    .componentDidMount(scope =>
      Callback.future {
        val ProjectPage(groupId, artifactId) = page
        AutowireClient[Api].projectPage(groupId, artifactId).call().map( r => 
          scope.modState{ case _ => r}
        )
      }
    )
    .build

  val component = ReactComponentB[ProjectPage]("Project Page View")
    .render_P( projectPage =>
      div(View(projectPage)())
    )
    .build
}