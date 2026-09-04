package scaladex.core.api

import scaladex.core.model.*
import scaladex.core.model.search.Page
import scaladex.core.model.search.PageParams

import endpoints4s.Invalid
import endpoints4s.Valid
import endpoints4s.Validated
import endpoints4s.algebra.BasicAuthentication
import endpoints4s.algebra.BasicAuthentication.Credentials

trait Endpoints
    extends JsonSchemas
    with endpoints4s.algebra.Endpoints
    with endpoints4s.algebra.JsonEntitiesFromSchemas
    with BasicAuthentication:

  val v0 = None
  val v1: Some[String] = Some("v1")

  private val root: Path[Unit] = path / "api"
  private def api(version: Option[String]): Path[Unit] = version.fold(root)(version => root / version)
  private val projectsPath: Path[Unit] = staticPathSegment("projects")
  private val artifactsPath: Path[Unit] = staticPathSegment("artifacts")

  private val organizationSegment: Path[Project.Organization] =
    segment[Project.Organization]("organization")(stringSegment.xmap(Project.Organization(_))(_.value))
  private val repositorySegment: Path[Project.Repository] =
    segment[Project.Repository]("repository")(stringSegment.xmap(Project.Repository(_))(_.value))
  private val groupIdSegment: Path[Artifact.GroupId] =
    segment[Artifact.GroupId]("groupId")(stringSegment.xmap(Artifact.GroupId(_))(_.value))
  private val artifactIdSegment: Path[Artifact.ArtifactId] =
    segment[Artifact.ArtifactId]("artifactId")(stringSegment.xmap(Artifact.ArtifactId(_))(_.value))
  private val versionSegment: Path[Version] = segment[Version]("version")(stringSegment.xmap(Version(_))(_.value))

  private val projectPath: Path[Project.Reference] =
    (projectsPath / organizationSegment / repositorySegment)
      .xmap((Project.Reference.apply _).tupled)(Tuple.fromProductTyped)

  private val projectVersionsPath: Path[Project.Reference] = projectPath / "versions"
  private val projectArtifactsPath: Path[Project.Reference] = projectPath / "artifacts"
  private val projectSettingsPath: Path[Project.Reference] = projectPath / "settings"
  private val projectDependenciesPath: Path[Project.Reference] = projectPath / "dependencies"
  private val projectDependentsPath: Path[Project.Reference] = projectPath / "dependents"
  private val usersPath: Path[Unit] = staticPathSegment("users")

  private val artifactPath: Path[Artifact.Reference] =
    (artifactsPath / groupIdSegment / artifactIdSegment / versionSegment)
      .xmap((Artifact.Reference.apply _).tupled)(Tuple.fromProductTyped)

  private given QueryStringParam[Version] = stringQueryString.xmap(Version(_))(_.value)
  private given QueryStringParam[Platform] = stringQueryString
    .xmapPartial(v => Validated.fromOption(Platform.parse(v))(s"Cannot parse $v"))(_.value)
  private given QueryStringParam[Language] = stringQueryString
    .xmapPartial(v => Validated.fromOption(Language.parse(v))(s"Cannot parse $v"))(_.value)
  private given QueryStringParam[BinaryVersion] = stringQueryString
    .xmapPartial(v => Validated.fromOption(BinaryVersion.parse(v))(s"Cannot parse $v"))(_.value)

  private val languageFilters = qs[Seq[Language]](
    "language",
    qsDoc("Filter on language versions", Seq("3", "2.13", "2.12", "2.11", "java"))
  )

  private val platformFilters = qs[Seq[Platform]](
    "platform",
    qsDoc("Filter on platform versions", Seq("jvm", "sjs1", "native0.5", "sbt1", "mill0.11"))
  )

  private val binaryVersionFilters: QueryString[Seq[BinaryVersion]] =
    qs(
      "binary-version",
      qsDoc("Filter on binary versions", Seq("_2.13", "_3", "_sjs1_3", "_2.12_1.0", "_mill0.11_2.13"))
    )

  private val binaryVersionFilter: QueryString[Option[BinaryVersion]] =
    qs[Option[BinaryVersion]](
      "binary-version",
      qsDoc("Filter on binary version", Seq("_2.13", "_3", "_sjs1_3", "_2.12_1.0", "_mill0.11_2.13"))
    )

  private val artifactNameFilters = qs[Seq[String]](
    "artifact-name",
    qsDoc("Filter on artifact names", Seq("cats-core", "cats-free"))
  ).xmap(_.map(Artifact.Name.apply))(_.map(_.value))

  private val artifactNameFilter = qs[Option[String]](
    "artifact-name",
    qsDoc("Filter on artifact name", Seq("cats-core", "cats-free"))
  ).xmap(_.map(Artifact.Name.apply))(_.map(_.value))

  private val stableOnlyFilter =
    qs[Option[Boolean]]("stable-only", Some("Keep only stable versions. (Default is true)"))
      .xmap(o => o.getOrElse(true))(b => Option.when(!b)(b))

  private val projectsParams: QueryString[ProjectsParams] =
    (languageFilters & platformFilters)
      .xmap((ProjectsParams.apply _).tupled)(Tuple.fromProductTyped)

  private val projectVersionsParams: QueryString[ProjectVersionsParams] =
    (binaryVersionFilters & artifactNameFilters & stableOnlyFilter)
      .xmap((ProjectVersionsParams.apply _).tupled)(Tuple.fromProductTyped)

  private val projectArtifactsParams: QueryString[ProjectArtifactsParams] =
    (binaryVersionFilter & artifactNameFilter & stableOnlyFilter)
      .xmap((ProjectArtifactsParams.apply _).tupled)(Tuple.fromProductTyped)

  private val versionFilter: QueryString[Option[Version]] =
    qs[Option[Version]]("version", Some("Version of the project to resolve dependencies for. Default is the latest."))

  private val pageParams: QueryString[PageParams] =
    (qs[Option[Int]]("page", Some("Page number, starting at 1. (Default is 1)")) &
      qs[Option[Int]](
        "size",
        Some(
          s"Number of items per page, one of ${PageParams.AllowedSizes.mkString(", ")}. (Default is ${PageParams.DefaultSize})"
        )
      ))
      .xmapPartial {
        case (page, size) =>
          val resolvedSize = size.getOrElse(PageParams.DefaultSize)
          if PageParams.AllowedSizes.contains(resolvedSize) then
            Valid(PageParams(page.getOrElse(1).max(1), resolvedSize))
          else Invalid(s"size must be one of ${PageParams.AllowedSizes.mkString(", ")}")
      } { params => (Some(params.page), Some(params.size)) }

  private val projectSearchParams: QueryString[ProjectSearchParams] = (
    qs[Option[String]]("q", Some("Main query (e.g., 'json', 'testing', etc.)")).xmap(_.getOrElse("*"))(Some(_)) &
      qs[Seq[String]]("topic", Some("Filter on Github topics")) &
      languageFilters &
      platformFilters &
      qs[Option[String]]("sort", qsDoc("Sort results", Seq("stars", "commit-activity", "contributors", "dependent")))
  ).xmap((ProjectSearchParams.apply _).tupled)(Tuple.fromProductTyped)

  private val autocompletionParams: QueryString[AutocompletionParams] = (
    qs[String]("q", docs = Some("Main query (e.g., 'json', 'testing', etc.)")) &
      qs[Seq[String]]("topic", docs = Some("Filter on Github topics")) &
      languageFilters &
      platformFilters &
      qs[Option[Boolean]]("contributingSearch").xmap(_.getOrElse(false))(Option.when(_)(true)) &
      qs[Option[String]]("you", docs = Some("internal usage")).xmap[Boolean](_.contains("✓"))(Option.when(_)("✓"))
  ).xmap((AutocompletionParams.apply _).tupled)(Tuple.fromProductTyped)

  def getProjects(v: Option[String]): Endpoint[ProjectsParams, Seq[Project.Reference]] =
    endpoint(
      get(api(v) / projectsPath /? projectsParams),
      ok(jsonResponse[Seq[Project.Reference]])
    )

  val getProjectV1: Endpoint[Project.Reference, Option[ProjectResponse]] =
    endpoint(get(api(v1) / projectPath), ok(jsonResponse[ProjectResponse]).orNotFound())

  val getProjectVersionsV1: Endpoint[(Project.Reference, ProjectVersionsParams), Seq[Version]] =
    endpoint(get(api(v1) / projectVersionsPath /? projectVersionsParams), ok(jsonResponse[Seq[Version]]))

  val getLatestProjectVersionV1: Endpoint[Project.Reference, Seq[Artifact.Reference]] =
    endpoint(get(api(v1) / projectVersionsPath / "latest"), ok(jsonResponse[Seq[Artifact.Reference]]))

  val getProjectVersionV1: Endpoint[(Project.Reference, Version), Seq[Artifact.Reference]] =
    endpoint(get(api(v1) / projectVersionsPath / versionSegment), ok(jsonResponse[Seq[Artifact.Reference]]))

  def getProjectArtifacts(
      v: Option[String]
  ): Endpoint[(Project.Reference, ProjectArtifactsParams), Seq[Artifact.Reference]] =
    endpoint(
      get(api(v) / projectArtifactsPath /? projectArtifactsParams),
      ok(jsonResponse[Seq[Artifact.Reference]])
    )

  def getArtifactVersions(
      v: Option[String]
  ): Endpoint[(Artifact.GroupId, Artifact.ArtifactId, Boolean), Seq[Version]] =
    endpoint(
      get(api(v) / artifactsPath / groupIdSegment / artifactIdSegment /? stableOnlyFilter),
      ok(jsonResponse[Seq[Version]])
    )

  def getLatestArtifactV1: Endpoint[(Artifact.GroupId, Artifact.ArtifactId), Option[ArtifactResponse]] =
    endpoint(
      get(api(v1) / artifactsPath / groupIdSegment / artifactIdSegment / "latest"),
      ok(jsonResponse[ArtifactResponse]).orNotFound()
    )

  def getArtifact(v: Option[String]): Endpoint[Artifact.Reference, Option[ArtifactResponse]] =
    endpoint(get(api(v) / artifactPath), ok(jsonResponse[ArtifactResponse]).orNotFound())

  val autocomplete: Endpoint[AutocompletionParams, Seq[AutocompletionResponse]] =
    endpoint(get(root / "autocomplete" /? autocompletionParams), ok(jsonResponse[Seq[AutocompletionResponse]]))

  val searchProjectsV1: Endpoint[(ProjectSearchParams, PageParams), Page[SearchResult]] =
    endpoint(
      get(api(v1) / "search" /? (projectSearchParams & pageParams)),
      ok(jsonResponse[Page[SearchResult]])
    )

  val getProjectDependenciesV1
      : Endpoint[(Project.Reference, Option[Version], PageParams), Option[Page[ProjectDependency]]] =
    endpoint(
      get(api(v1) / projectDependenciesPath /? (versionFilter & pageParams)),
      ok(jsonResponse[Page[ProjectDependency]]).orNotFound()
    )

  val getProjectDependentsV1: Endpoint[(Project.Reference, PageParams), Option[Page[ProjectDependency]]] =
    endpoint(
      get(api(v1) / projectDependentsPath /? pageParams),
      ok(jsonResponse[Page[ProjectDependency]]).orNotFound()
    )

  val getAuthenticatedUserV1: Endpoint[Credentials, Option[UserResponse]] =
    authenticatedEndpoint(Get, api(v1) / usersPath / "me", ok(jsonResponse[UserResponse]))

  val getProjectSettingsV1: Endpoint[(Project.Reference, Credentials), Option[Option[Project.Settings]]] =
    authenticatedEndpoint(Get, api(v1) / projectSettingsPath, ok(jsonResponse[Project.Settings]).orNotFound())

  // Read-modify-write with no optimistic locking: two patches racing on the same project can lose an update, same as
  // the web settings form.
  val patchProjectSettingsV1: Endpoint[(Project.Reference, ProjectSettingsPatch, Credentials), Option[Option[Unit]]] =
    authenticatedEndpoint(
      Patch,
      api(v1) / projectSettingsPath,
      ok(emptyResponse).orNotFound(),
      requestEntity = jsonRequest[ProjectSettingsPatch]
    )

  private def qsDoc(desc: String, examples: Seq[String]): Some[String] =
    Some(
      s"""|$desc.
          |Examples: ${examples.mkString(", ")}""".stripMargin
    )
end Endpoints
