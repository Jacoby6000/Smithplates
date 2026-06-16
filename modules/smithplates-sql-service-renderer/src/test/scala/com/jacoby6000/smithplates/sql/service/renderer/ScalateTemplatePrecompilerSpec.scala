package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.scalate.precompiler.ScalateTemplatePrecompiler
import org.fusesource.scalate.Template
import org.fusesource.scalate.TemplateSource

import java.io.File
import java.nio.file.Files

/** Guards the precompilation contract: the class name the precompiler generates for a template must equal the class
  * name the runtime [[org.fusesource.scalate.TemplateEngine]] resolves via `TemplateSource.className`, and precompiled
  * classes on the classpath must be loaded instead of being recompiled.
  */
class ScalateTemplatePrecompilerSpec extends munit.FunSuite {
  private val templateRoot = "python/src/db"

  private def dbTemplateDirectory: File =
    Option(getClass.getResource(s"/$templateRoot"))
      .map(url => new File(url.toURI))
      .getOrElse(fail(s"bundled templates not found on classpath at /$templateRoot"))

  private def sspFiles(directory: File): Seq[File] =
    Option(directory.listFiles()).map(_.toSeq).getOrElse(Seq.empty).flatMap { entry =>
      if (entry.isDirectory) {
        sspFiles(entry)
      } else if (entry.getName.endsWith(".ssp")) {
        Seq(entry)
      } else {
        Seq.empty
      }
    }

  private def relativeUri(root: File, file: File): String =
    root.toURI.relativize(file.toURI).getPath

  test("generated class name matches the runtime class name for both URI conventions") {
    val engine        = ScalateSspTemplateEngine.precompilationEngine(templateRoot)
    val templateFiles = sspFiles(dbTemplateDirectory)
    assert(templateFiles.nonEmpty, "expected bundled SQL templates to enumerate")

    templateFiles.foreach { templateFile =>
      val relative = relativeUri(dbTemplateDirectory, templateFile)
      if (!relative.endsWith("preamble.ssp")) {
        Seq(relative, s"$templateRoot/$relative").foreach { uri =>
          val source    = TemplateSource.fromUri(uri, engine.resourceLoader)
          source.engine = engine
          val generated = engine.generateScala(uri)
          assertEquals(
            generated.className,
            source.className,
            s"generated class name diverged from runtime class name for $uri"
          )
          assert(generated.source.nonEmpty, s"generated source was empty for $uri")
        }
      }
    }
  }

  test("class names are namespaced per template root by package prefix") {
    val engine = ScalateSspTemplateEngine.precompilationEngine(templateRoot)
    val source = TemplateSource.fromUri("models/models.ssp", engine.resourceLoader)
    source.engine = engine
    assert(
      source.className.startsWith(ScalateTemplatePrecompiler.packagePrefix(templateRoot)),
      s"expected ${source.className} to start with the per-root package prefix"
    )
  }

  test("a precompiled class on the classpath is loaded instead of recompiled") {
    val templateUri = "models/models.ssp"

    val engine = ScalateSspTemplateEngine.precompilationEngine(templateRoot)
    // A fresh, empty working directory so any compilation would be observable here.
    engine.workingDirectory = Files.createTempDirectory("smithplates-precompiled-load").toFile

    val loaded = engine.load(templateUri)

    // The precompiled class for this template is published onto the test classpath
    // (Test / unmanagedClasspath), so Scalate must resolve it directly instead of
    // recompiling the .ssp source. If recompilation happened, the ScalaCompiler would
    // have emitted class files into the engine's bytecode directory.
    val recompiled =
      Option(engine.bytecodeDirectory.listFiles()).map(_.toSeq).getOrElse(Seq.empty).nonEmpty
    assert(
      !recompiled,
      s"expected ${loaded.getClass.getName} to load a precompiled class without recompiling into " +
        s"${engine.bytecodeDirectory}"
    )
    assert(classOf[Template].isAssignableFrom(loaded.getClass), "loaded class is not a Scalate Template")
  }
}
