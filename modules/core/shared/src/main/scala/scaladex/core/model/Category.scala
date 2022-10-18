package scaladex.core.model

sealed trait Category {
  val label: String = {
    val name = getClass.getSimpleName.stripSuffix("$")
    val builder = name.foldLeft(new StringBuilder) { (builder, char) =>
      if (char.isUpper && builder.nonEmpty) builder += '-'
      builder += char.toLower
    }
    builder.toString
  }

  val title: String = {
    val name = getClass.getSimpleName.stripSuffix("$")
    val builder = name.foldLeft(new StringBuilder) { (builder, char) =>
      if (char.isUpper && builder.nonEmpty) builder += ' '
      builder += char
    }
    builder.toString.replaceAll(" And ", " and ")
  }
}

object Category {
  implicit val ordering: Ordering[Category] = Ordering.by(_.label)
  val all: Seq[Category] = MetaCategory.all.flatMap(_.categories).distinct
  val byLabel: Map[String, Category] = all.map(category => category.label -> category).toMap

  case object AsynchronousAndReactiveProgramming extends Category
  case object DistributedComputing extends Category
  case object DistributedMessagingSystemsAndMicroservices extends Category
  case object Schedulers extends Category

  case object AudioAndMusic extends Category
  case object VideoAndImageProcessing extends Category

  case object DataSourcesAndConnectors extends Category
  case object DataVizualization extends Category

  case object CommandLineParsing extends Category
  case object ConfigurationAndEnvironment extends Category
  case object Logging extends Category
  case object PerformanceAndMonitoring extends Category
  case object Testing extends Category

  case object Databases extends Category
  case object IndexingAndSearching extends Category

  case object DatesAndTime extends Category
  case object GeometryAndGeopositionning extends Category
  case object UnitsOfMeasurement extends Category

  case object DeploymentAndCloud extends Category
  case object PackagingAndPublishing extends Category
  case object LibraryDependencyManagement extends Category
  case object Serverless extends Category
  case object VersionManagement extends Category
  case object VirtualizationAndContainerization extends Category

  case object BuildTools extends Category
  case object CodeAnalysis extends Category
  case object LintingAndRefactoring extends Category
  case object PrintingAndDebugging extends Category
  case object CodeEditorsAndNotebooks extends Category
  case object CodeFormatting extends Category
  case object ScriptingAndRepls extends Category {
    override val title: String = "Scripting and REPLs"
  }
  case object StaticSitesAndDocumentation extends Category
  case object MiscellaneousTools extends Category

  case object AlgorithmsAndDataStructures extends Category
  case object Caching extends Category
  case object Compilers extends Category
  case object CodeGeneration extends Category
  case object DependencyInjection extends Category
  case object FunctionnalProgrammingAndCategoryTheory extends Category
  case object LogicProgrammingAndTypeConstraints extends Category
  case object MiscellaneousUtils extends Category
  case object Parsing extends Category
  case object ScalaLanguageExtensions extends Category
  case object ProgrammingLanguageInterfaces extends Category

  case object Bioinformatics extends Category
  case object CryptographyAndHashing extends Category
  case object EconomyFinanceAndCryptocurrencies extends Category {
    override val title: String = "Economy, Finance and Cryptocurrencies"
  }
  case object ProbabilityStatisticsAndMachineLearning extends Category {
    override val title: String = "Probability, Statistics and Machine Learning"
  }
  case object NaturalLanguageProcessing extends Category
  case object NumericalAndSymbolicComputing extends Category

  case object Mobile extends Category
  case object GraphicalInterfacesAndGameDevelopment extends Category

  case object HardwareAndEmulators extends Category
  case object FileSystemsAndProcesses extends Category
  case object Network extends Category

  case object ArchivesAndCompression extends Category
  case object Csv extends Category {
    override val title: String = "CSV"
  }
  case object Json extends Category {
    override val title: String = "JSON"
  }
  case object Markdown extends Category
  case object Pdf extends Category {
    override val title: String = "PDF"
  }
  case object Serialization extends Category
  case object Templating extends Category
  case object TextManipulation extends Category
  case object OtherDocumentFormats extends Category
  case object Yaml extends Category {
    override val title: String = "YAML"
  }
  case object XmlHtmlAndDom extends Category {
    override val title: String = "XML/HTML and DOM"
  }

  case object AssetManagementAndBundlers extends Category
  case object AuthenticationAndPermissions extends Category
  case object Emailing extends Category
  case object FormsAndValidation extends Category
  case object HttpServersAndClients extends Category {
    override val title: String = "HTTP Servers and Clients"
  }
  case object Internationalization extends Category
  case object ThirdPartyApis extends Category {
    override val title: String = "Third-Party APIs"
  }
  case object UrlsAndRouting extends Category {
    override val title: String = "URLs and Routing"
  }
  case object WebFrontend extends Category
  case object SemanticWeb extends Category
}
