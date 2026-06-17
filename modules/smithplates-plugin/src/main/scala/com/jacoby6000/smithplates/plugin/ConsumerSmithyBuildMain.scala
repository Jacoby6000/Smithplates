package com.jacoby6000.smithplates.plugin

import java.nio.file.Paths

object ConsumerSmithyBuildMain {
  def main(args: Array[String]): Unit =
    if (args.length != 1) {
      System.err.println("usage: ConsumerSmithyBuildMain <project-directory>")
      sys.exit(2)
    } else {
      val projectDirectory = Paths.get(args(0)).toAbsolutePath.normalize()
      ConsumerSmithyBuild.run(
        projectDirectory,
        Thread.currentThread().getContextClassLoader
      ) match {
        case Left(message) =>
          System.err.println(message)
          sys.exit(1)
        case Right(_)      =>
          ()
      }
    }
}
