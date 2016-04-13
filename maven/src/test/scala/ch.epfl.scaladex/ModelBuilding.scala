import utest._

import org.apache.maven.model._
import resolution._
import io._
import building._ 
import java.io.File
import java.nio.file.Paths
import java.util.Properties

object ModelBuildingTests extends TestSuite{

  def file(path: String) = new File(this.getClass.getResource(path).toURI)
  val tests = this{
    "spark"-{
      val builder = (new DefaultModelBuilderFactory).newInstance
      val request = new DefaultModelBuildingRequest
      val processor = new DefaultModelProcessor
      
      val rez = new ModelResolver {
        def addRepository(repo: Repository, replace: Boolean): Unit = ()
        def addRepository(repo: Repository): Unit = ()
        def newCopy(): resolution.ModelResolver = throw new Exception("copy")   
        def resolveModel(parent: Parent): ModelSource2 = {
          resolveModel(parent.getGroupId, parent.getArtifactId, parent.getVersion)
        }
        def resolveModel(groupId: String, artifactId: String, version: String): ModelSource2 = {
          val path = groupId.split('.').toList ::: List(s"$artifactId-$version.pom")
          new FileModelSource(file(path.mkString(File.separator)))
        }
      }
      
      val jdk = new Properties
      jdk.setProperty("java.version", "1.8")
      
      request
        .setModelResolver(rez)
        .setPomFile(file("/org/apache/spark/spark-core_2.11-1.6.1.pom"))
        .setSystemProperties(jdk)
      
      processor.setModelReader(new DefaultModelReader)
      
      import collection.JavaConverters._
      builder.build(request).getEffectiveModel.getDependencies.asScala.size ==> 61
    }
  }
}