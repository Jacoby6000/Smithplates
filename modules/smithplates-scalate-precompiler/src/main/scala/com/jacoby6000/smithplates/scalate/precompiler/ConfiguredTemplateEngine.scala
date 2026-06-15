package com.jacoby6000.smithplates.scalate.precompiler

import org.fusesource.scalate.TemplateEngine
import org.fusesource.scalate.support.Compiler
import org.fusesource.scalate.support.ScalaCompiler

import java.io.File

/** A [[org.fusesource.scalate.support.ScalaCompiler]] that appends the shared template compiler options
  * ([[ScalateTemplatePrecompiler.templateScalacOptions]]) to Scalate's defaults.
  *
  * Scalate's `ScalaCompiler` only passes `-classpath` and `-d`; it has no hook for additional Scala compiler options,
  * so we override the (protected) settings builder. The override reads the options from the
  * [[ScalateTemplatePrecompiler]] module rather than instance state, which keeps it safe to call from the superclass
  * constructor (where `settings` is evaluated) without tripping initialization order.
  */
final class ConfiguredScalaCompiler(
    bytecodeDirectory: File,
    classpath: String,
    combineClasspath: Boolean
) extends ScalaCompiler(bytecodeDirectory, classpath, combineClasspath) {
  override protected def generateSettings(
      bytecodeDirectory: File,
      classpath: String,
      combineClasspath: Boolean
  ): Seq[String] =
    super.generateSettings(bytecodeDirectory, classpath, combineClasspath) ++
      ScalateTemplatePrecompiler.templateScalacOptions
}

/** A [[org.fusesource.scalate.TemplateEngine]] whose runtime fallback compiler uses the same Scala compiler options as
  * the build-time precompiler. Renderers should construct this instead of a bare `TemplateEngine` so that, on the rare
  * occasions a template is compiled at runtime (for example in tests, where precompiled classes are not on the
  * classpath), the diagnostics match the build and the pervasive optional-braces indentation warnings are not emitted.
  */
final class ConfiguredTemplateEngine extends TemplateEngine {
  override protected def createCompiler: Compiler = {
    compilerInitialized = true
    new ConfiguredScalaCompiler(bytecodeDirectory, classpath, combinedClassPath)
  }
}
