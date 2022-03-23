package scaladex.core.model

sealed trait MetaCategory {
  val label: String = {
    val name = getClass.getSimpleName.stripSuffix("$")
    val builder = name.foldLeft(new StringBuilder) { (builder, char) =>
      if (char.isUpper && builder.nonEmpty) builder += '-'
      builder += char.toLower
    }
    builder.toString
  }

  val title: String
  val categories: Seq[Category]
  val seeAlsoCategories: Seq[Category] = Seq.empty
}

object MetaCategory {
  def all: Seq[MetaCategory] = Seq(
    AsynchronousAndConcurrentProgramming,
    BigData,
    ComputerScience,
    ConfigurationLoggingTestingAndMonitoring,
    DatabasesIndexingAndSearching,
    DeploymentVirtualizationAndCloud,
    DevelopmentTooling,
    ImagesAudioAndVideo,
    MathematicsFinanceDataScienceAndBioinformatics,
    MobileDesktopAndGameDevelopment,
    OperatingSystemsAndHardware,
    TextFormatAndCompression,
    TimePositionsAndUnitsOfMeasurement,
    WebDevelopment
  )

  val byLabel: Map[String, MetaCategory] = all.map(meta => meta.label -> meta).toMap

  case object AsynchronousAndConcurrentProgramming extends MetaCategory {
    override val title: String = "Asynchronous, Concurrent and Distributed Programming"

    override val categories: Seq[Category] = Seq(
      Category.AsynchronousAndReactiveProgramming,
      Category.DistributedMessagingSystemsAndMicroservices,
      Category.Schedulers
    )
    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.DistributedComputing
    )
  }
  case object BigData extends MetaCategory {
    override val title: String = "Big Data"
    override val categories: Seq[Category] = Seq(
      Category.DataSourcesAndConnectors,
      Category.DataVizualization,
      Category.DistributedComputing
    )

    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.Databases,
      Category.IndexingAndSearching,
      Category.ProbabilityStatisticsAndMachineLearning
    )
  }

  case object ComputerScience extends MetaCategory {
    override val title: String = "Computer Science"
    override val categories: Seq[Category] = Seq(
      Category.AlgorithmsAndDataStructures,
      Category.Caching,
      Category.CodeGeneration,
      Category.Compilers,
      Category.DependencyInjection,
      Category.FunctionnalProgrammingAndCategoryTheory,
      Category.LogicProgrammingAndTypeConstraints,
      Category.MiscellaneousUtils,
      Category.Parsing,
      Category.ProgrammingLanguageInterfaces,
      Category.ScalaLanguageExtensions
    )
  }

  case object ConfigurationLoggingTestingAndMonitoring extends MetaCategory {
    override val title: String = "Configuration, Logging, Testing and Monitoring"
    override val categories: Seq[Category] = Seq(
      Category.CommandLineParsing,
      Category.ConfigurationAndEnvironment,
      Category.Logging,
      Category.PerformanceAndMonitoring,
      Category.Testing
    )
  }
  case object DatabasesIndexingAndSearching extends MetaCategory {
    override val title: String = "Databases, Indexing and Searching"
    override val categories: Seq[Category] = Seq(
      Category.Databases,
      Category.IndexingAndSearching
    )
  }
  case object DeploymentVirtualizationAndCloud extends MetaCategory {
    override val title: String = "Deployment, Virtualization and Cloud"
    override val categories: Seq[Category] = Seq(
      Category.DeploymentAndCloud,
      Category.Serverless,
      Category.VersionManagement,
      Category.VirtualizationAndContainerization
    )

    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.ConfigurationAndEnvironment,
      Category.PackagingAndPublishing,
      Category.Yaml
    )
  }
  case object DevelopmentTooling extends MetaCategory {
    override val title = "Development Tooling"
    override val categories: Seq[Category] = Seq(
      Category.BuildTools,
      Category.CodeAnalysis,
      Category.CodeEditorsAndNotebooks,
      Category.CodeFormatting,
      Category.LibraryDependencyManagement,
      Category.LintingAndRefactoring,
      Category.MiscellaneousTools,
      Category.PackagingAndPublishing,
      Category.PrintingAndDebugging,
      Category.ScriptingAndRepls,
      Category.StaticSitesAndDocumentation
    )

    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.Testing
    )
  }
  case object ImagesAudioAndVideo extends MetaCategory {
    override val title: String = "Images, Audio and Video"
    override val categories: Seq[Category] = Seq(
      Category.AudioAndMusic,
      Category.VideoAndImageProcessing
    )
    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.GraphicalInterfacesAndGameDevelopment
    )
  }

  case object MathematicsFinanceDataScienceAndBioinformatics extends MetaCategory {
    override val title: String = "Mathematics, Finance, Data Science and Bioinformatics"
    override val categories: Seq[Category] = Seq(
      Category.Bioinformatics,
      Category.CryptographyAndHashing,
      Category.EconomyFinanceAndCryptocurrencies,
      Category.NaturalLanguageProcessing,
      Category.NumericalAndSymbolicComputing,
      Category.ProbabilityStatisticsAndMachineLearning
    )

    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.GeometryAndGeopositionning,
      Category.UnitsOfMeasurement
    )
  }
  case object MobileDesktopAndGameDevelopment extends MetaCategory {
    override val title: String = "Mobile, Desktop and Game Development"
    override val categories: Seq[Category] = Seq(
      Category.GraphicalInterfacesAndGameDevelopment,
      Category.Mobile
    )
  }
  case object OperatingSystemsAndHardware extends MetaCategory {
    override val title: String = "Operating System, Hardware and Robotics"
    override val categories: Seq[Category] = Seq(
      Category.FileSystemsAndProcesses,
      Category.HardwareAndEmulators,
      Category.Network
    )

    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.Mobile
    )
  }
  case object TextFormatAndCompression extends MetaCategory {
    override val title: String = "Text, Formats and Compression"
    override val categories: Seq[Category] = Seq(
      Category.ArchivesAndCompression,
      Category.Csv,
      Category.Json,
      Category.Markdown,
      Category.OtherDocumentFormats,
      Category.Pdf,
      Category.Serialization,
      Category.TextManipulation,
      Category.Yaml
    )

    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.XmlHtmlAndDom
    )
  }
  case object TimePositionsAndUnitsOfMeasurement extends MetaCategory {
    override val title: String = "Time, Positions and Units of Measurement"
    override val categories: Seq[Category] = Seq(
      Category.DatesAndTime,
      Category.GeometryAndGeopositionning,
      Category.UnitsOfMeasurement
    )
  }
  case object WebDevelopment extends MetaCategory {
    override val title: String = "Web Development"
    override val categories: Seq[Category] = Seq(
      Category.AssetManagementAndBundlers,
      Category.AuthenticationAndPermissions,
      Category.Emailing,
      Category.FormsAndValidation,
      Category.HttpServersAndClients,
      Category.Internationalization,
      Category.SemanticWeb,
      Category.Templating,
      Category.ThirdPartyApis,
      Category.UrlsAndRouting,
      Category.WebFrontend,
      Category.XmlHtmlAndDom
    )

    override val seeAlsoCategories: Seq[Category] = Seq(
      Category.Json
    )
  }
}
