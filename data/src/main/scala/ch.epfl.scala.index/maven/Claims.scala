package ch.epfl.scala.index
package maven

// script to generate contrib/claims.json
object Claims {
  def run(): String = {
    import scala.util._
    val poms = Poms.get.collect{ case Success(p) => PomConvert(p) }
    val noUrl = poms.filter(p => 
      (p.scm match {
        case Some(v) => {
          import v._        
          List(connection,developerConnection, url).flatten.flatMap(CleanUp.parseRepo)
        }
        case None => List()
      }).size == 0
    )
    
    noUrl.sortBy(d => (d.groupId, d.artifactId)).map{d =>
      import d._
      s"'$groupId $artifactId $version': null"
    }.mkString("", "," + System.lineSeparator, "")
  }
}