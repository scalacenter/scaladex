package scaladex.server.route

import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.temporal.ChronoUnit

import scaladex.core.model.Artifact
import scaladex.core.model.DiscoveredGroupId
import scaladex.core.model.Env
import scaladex.core.model.GithubInfo
import scaladex.core.model.Project
import scaladex.core.test.MockGithubAuth

import org.scalatest.funspec.AnyFunSpec

/** Not a real test: renders the admin page with synthetic discovery data to an HTML file so the panel can be eyeballed
  * without the full stack. Set `-Dscaladex.admin.preview=/path/to/out.html`.
  */
class AdminPagePreview extends AnyFunSpec:
  it("renders the admin page to an html file") {
    // opt-in only: skipped in CI, run with `-Dscaladex.admin.preview=/path/out.html`
    val out = sys.props.getOrElse("scaladex.admin.preview", cancel("set -Dscaladex.admin.preview to render"))

    def project(org: String, repo: String, stars: Int, scalaPct: Int): Project =
      Project.default(
        Project.Reference.from(org, repo),
        githubInfo = Some(GithubInfo.empty.copy(stars = Some(stars), scalaPercentage = Some(scalaPct)))
      )

    val now = Instant.now
    def discovered(
        groupId: String,
        ageHours: Long,
        summary: Option[String],
        projects: Seq[Project]
    ): DiscoveredGroupId.View =
      val d = DiscoveredGroupId
        .pending(DiscoveredGroupId.Source.MavenIndex, Artifact.GroupId(groupId), now.minus(ageHours, ChronoUnit.HOURS))
        .copy(
          lastSyncedAt = summary.map(_ => now.minus(ageHours - 1, ChronoUnit.HOURS)),
          syncSummary = summary,
          projectRefs = projects.map(_.reference)
        )
      DiscoveredGroupId.View(d, projects)
    end discovered

    val views = Seq(
      discovered(
        "dev.valentiay",
        6,
        Some("Inserted 29 poms"),
        Seq(project("valentiay", "phobos", 41, 96))
      ),
      discovered(
        "com.softwaremill.sttp.ai",
        14,
        Some("Inserted 12 poms"),
        Seq(project("softwaremill", "sttp-ai", 73, 99))
      ),
      discovered("io.github.zyblw", 20, Some("github repository not found"), Nil),
      discovered("org.mongo4s", 30, None, Nil)
    )

    val html = scaladex.view.admin.html.admin(
      Env.Local,
      MockGithubAuth.Admin.userState,
      jobs = Nil,
      tasks = Nil,
      discovered = views
    )
    Files.writeString(Paths.get(out), html.body)
    info(s"wrote $out")
  }
end AdminPagePreview
