import scala.reflect.ClassTag

object CurrentThread {
  def setContextClassLoader[T: ClassTag]: Unit = {
    val currentThread = Thread.currentThread()
    currentThread.setContextClassLoader(
      implicitly[ClassTag[T]].runtimeClass.getClassLoader()
    )
  }

  def setContextClassLoader(className: String): Unit = {
    val currentThread = Thread.currentThread()
    val classLoader = Class.forName(className).getClassLoader()
    currentThread.setContextClassLoader(classLoader)
  }
}
