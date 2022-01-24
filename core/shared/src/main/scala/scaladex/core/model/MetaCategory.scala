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
}

object MetaCategory {
  def all: Seq[MetaCategory] = Seq(
    AsynchronousConcurrentAndDistributedProgramming,
    AudioImagesAndVideo,
    BigData,
    ConfigurationLoggingTestingAndMonitoring,
    DatabasesIndexingAndSearching,
    TimePositionsAndUnitsOfMeasurement,
    DeploymentVirtualizationAndCloud,
    DevelopmentTooling,
    GeneralComputerScience,
    MathematicsFinanceDataScienceAndBioinformatics,
    MobileDesktopAndGameDevelopment,
    OperatingSystemsAndHardware,
    TextFormatAndCompression,
    WebDevelopment
  )

  case object AsynchronousConcurrentAndDistributedProgramming extends MetaCategory {
    override val title: String = "Asynchronous, Concurrent and Distributed Programming."

    override val categories: Seq[Category] = Seq(
      Category.AsynchronousAndReactiveProgramming,
      Category.DistributedComputing,
      Category.DistributedMessagingSystemsAndMicroservices,
      Category.Schedulers
    )
  }
  case object AudioImagesAndVideo extends MetaCategory {
    override val title: String = "Audio, Images and Video"
    override val categories: Seq[Category] = Seq(
      Category.AudioAndMusic,
      Category.VideoAndImageProcessing
    )
  }
  case object BigData extends MetaCategory {
    override val title: String = "Big Data"
    override val categories: Seq[Category] = Seq(
      Category.DataSourcesAndConnectors,
      Category.DataVizualization,
      Category.DistributedComputing
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
  case object TimePositionsAndUnitsOfMeasurement extends MetaCategory {
    override val title: String = "Dates, Time, Positions and Units of Measurement"
    override val categories: Seq[Category] = Seq(
      Category.DatesAndTime,
      Category.GeometryAndGeopositionning,
      Category.UnitsOfMeasurement
    )
  }
  case object DeploymentVirtualizationAndCloud extends MetaCategory {
    override val title: String = "Deployment, Virtualization and Cloud"
    override val categories: Seq[Category] = Seq(
      Category.DeploymentAndCloud,
      Category.PackagingAndPublishing,
      Category.LibraryDependencyManagement,
      Category.Serverless,
      Category.VersionManagement,
      Category.VirtualizationAndContainerization
    )
  }
  case object DevelopmentTooling extends MetaCategory {
    override val title = "Development Tooling"
    override val categories: Seq[Category] = Seq(
      Category.BuildTools,
      Category.CodeAnalysis,
      Category.LintingAndRefactoring,
      Category.PrintingAndDebugging,
      Category.CodeEditorsAndNotebooks,
      Category.CodeFormatting,
      Category.ScriptingAndRepls,
      Category.StaticSitesAndDocumentation,
      Category.MiscellaneousTools
    )
  }
  case object GeneralComputerScience extends MetaCategory {
    override val title: String = "General Computer Science"
    override val categories: Seq[Category] = Seq(
      Category.AlgorithmsAndDataStructures,
      Category.Caching,
      Category.Compilers,
      Category.CodeGeneration,
      Category.DependencyInjection,
      Category.FunctionnalProgrammingAndCategoryTheory,
      Category.LogicProgrammingAndTypeConstraints,
      Category.MiscellaneousUtils,
      Category.Parsing,
      Category.ScalaLanguageExtensions,
      Category.ProgrammingLanguageInterfaces
    )
  }
  case object MathematicsFinanceDataScienceAndBioinformatics extends MetaCategory {
    override val title: String = "Mathetmatics, Finance, Data Science and Bioinformatics"
    override val categories: Seq[Category] = Seq(
      Category.Bioinformatics,
      Category.CryptographyAndHashing,
      Category.EconomyFinanceAndCryptocurrencies,
      Category.ProbabilityStatisticsAndMachineLearning,
      Category.NaturalLanguageProcessing,
      Category.NumericalAndSymbolicComputing
    )
  }
  case object MobileDesktopAndGameDevelopment extends MetaCategory {
    override val title: String = "Mobile, Desktop and Game Development"
    override val categories: Seq[Category] = Seq(
      Category.Mobile,
      Category.GraphicalInterfacesAndGameDevelopment
    )
  }
  case object OperatingSystemsAndHardware extends MetaCategory {
    override val title: String = "Operating System, Hardware and Robotics"
    override val categories: Seq[Category] = Seq(
      Category.HardwareAndEmulators,
      Category.FileSystemsAndProcesses,
      Category.Network
    )
  }
  case object TextFormatAndCompression extends MetaCategory {
    override val title: String = "Text, Formats and Compression"
    override val categories: Seq[Category] = Seq(
      Category.ArchivesAndCompression,
      Category.Csv,
      Category.Json,
      Category.Markdown,
      Category.Pdf,
      Category.Serialization,
      Category.TextManipulation,
      Category.OtherDocumentFormats,
      Category.Yaml
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
  }
}
