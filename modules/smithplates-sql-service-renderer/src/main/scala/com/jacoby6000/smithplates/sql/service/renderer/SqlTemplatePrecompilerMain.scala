package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.scalate.precompiler.PrecompileRoot
import com.jacoby6000.smithplates.scalate.precompiler.ScalateTemplatePrecompiler

import java.io.File

/** Build-time entry point that ahead-of-time compiles the bundled SQL service SSP templates into JVM classes.
  *
  * Invoked from the build (see `build.sbt`) on the renderer module classpath so that generated template classes are
  * packaged into the published renderer jar. Arguments:
  *
  * {{{
  *   <outputClassesDir> <generatedSourcesDir> <templateRoot> <templateSourceDir> [<templateRoot> <templateSourceDir> ...]
  * }}}
  */
object SqlTemplatePrecompilerMain {
  def main(args: Array[String]): Unit = {
    if (args.length < 4 || (args.length % 2) != 0) {
      throw new IllegalArgumentException(
        "Usage: SqlTemplatePrecompilerMain <outputClassesDir> <generatedSourcesDir> " +
          "<templateRoot> <templateSourceDir> [<templateRoot> <templateSourceDir> ...]"
      )
    }

    val outputDirectory          = new File(args(0))
    val generatedSourceDirectory = new File(args(1))
    val roots                    =
      args.drop(2).grouped(2).map(pair => PrecompileRoot(pair(0), new File(pair(1)))).toSeq

    ScalateTemplatePrecompiler.precompile(
      ScalateSspTemplateEngine.precompilationEngine,
      roots,
      outputDirectory,
      generatedSourceDirectory,
      message => println(message)
    )
  }
}
