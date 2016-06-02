import sbt.{AutoPlugin, Command, State}
import sbt.Keys._

/**
  * Place holder plugin for setting up scripted.
  */
object HelloPlugin extends AutoPlugin {
  override lazy val projectSettings = Seq(commands += helloCommand)
  lazy val helloCommand =
    Command.command("hello") { (state: State) =>
      println("Hi!")
      state
    }
}
