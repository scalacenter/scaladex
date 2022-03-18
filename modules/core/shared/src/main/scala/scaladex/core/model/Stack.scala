package scaladex.core.model

sealed trait Stack {
  def name: String
  def label: String = name.toLowerCase.replace(' ', '-')
  def projects: Seq[Project.Reference]
  override def toString: String = name
}

object Stack {
  val all: Seq[Stack] = Seq(Akka, Lihaoyi, Spark, Typelevel, Zio)
  val byLabel: Map[String, Stack] = all.map(s => s.label -> s).toMap

  object Akka extends Stack {
    override val name: String = "Akka"
    override val projects: Seq[Project.Reference] = Seq("akka/akka", "akka/akka-http", "akka/alpakka").map(Project.Reference.from)
  }

  object Lihaoyi extends Stack {
    override val name: String = "Li Haoyi"
    override val projects: Seq[Project.Reference] = Seq("com-lihaoyi/ammonite", "com-liahoyi/mill", "com-lihaoyi/fastparse", "com-lihaoyi/scalatags", "com-lihaoyi/requests-scala", "com-lihaoyi/upickle", "com-lihaoyi/cask", "com-lihaoyi/mainargs", "com-lihaoyi/castor", "com-lihaoyi/geny").map(Project.Reference.from)
  }

  object Spark extends Stack {
    override val name: String = "Spark"
    override val projects: Seq[Project.Reference] = Seq("apache/spark").map(Project.Reference.from)
  }

  object Typelevel extends Stack {
    override val name: String = "Typelevel"
    override val projects: Seq[Project.Reference] =  Seq("typelevel/cats", "typelevel/cats-effect", "typelevel/fs2").map(Project.Reference.from)
  }

  object Zio extends Stack {
    override val name: String = "ZIO"
    override val projects: Seq[Project.Reference] = Seq("zio/zio", "zio/zio-prelude").map(Project.Reference.from)
  }

}
