package com.jacoby6000.smithplates.scalate.precompiler

import org.fusesource.scalate.TemplateEngine

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** A bundled SSP template root to precompile.
  *
  * @param templateRoot
  *   the classpath template root (for example `python/src/db`) used to configure the engine and to derive the
  *   root-prefixed URI form
  * @param sourceDirectory
  *   the directory whose tree enumerates the `.ssp` templates belonging to `templateRoot`
  */
final case class PrecompileRoot(templateRoot: String, sourceDirectory: File)

/** Ahead-of-time compiles Scalate SSP templates into JVM classes so that the published plugin can load precompiled
  * templates at `smithy build` time instead of invoking the Scala compiler for each template.
  *
  * The runtime [[org.fusesource.scalate.TemplateEngine]] looks for a precompiled class named after the template URI
  * before falling back to compilation (see `TemplateEngine.load` / `loadPrecompiledEntry`). This driver mirrors that
  * naming exactly by reusing the same engine configuration (resource loader, bindings and `packagePrefix`) for code
  * generation, then batch-compiles all generated sources in a single compiler invocation.
  */
object ScalateTemplatePrecompiler {

  /** Scala compiler options applied when compiling Scalate-generated template sources, both at build time (this
    * precompiler) and at runtime (the fallback [[org.fusesource.scalate.support.ScalaCompiler]] used by
    * [[ConfiguredTemplateEngine]]). A single source of truth keeps the two paths producing identical diagnostics and
    * byte-identical classes.
    *
    * These mirror the project's strict build options where they are meaningful for machine-generated code. Only
    * `-no-indent` is required: Scalate emits its template wrappers and our injected preamble in a mix of brace and
    * significant-indentation styles, which the Scala 3 optional-braces checker otherwise flags as "Line is indented too
    * far to the left, or a `}` is missing" on nearly every line. `-no-indent` matches the build's own `-no-indent` and
    * removes those warnings at the source rather than suppressing compiler output.
    *
    * The remaining strict options are deliberately NOT applied here: `-Wunused:all`, `-Wvalue-discard` and `-Werror`
    * would turn unavoidable properties of generated code (injected helper defs unused by a given template, discarded
    * render-context appends) into thousands of compile errors. Those checks exist to keep hand-written sources clean
    * and are enforced on the renderer modules themselves by the sbt build, not on generated templates.
    */
  val templateScalacOptions: Seq[String] = Seq("-no-indent")

  /** Generates Scala sources for every template under each root (excluding injected `preamble.ssp` fragments) and
    * compiles them in a single batch into `outputDirectory` as `.class` files.
    *
    * Each template is generated under both URI conventions Scalate uses at runtime so the precompiled class is found
    * regardless of how the template is loaded:
    *   - the root-relative form (top-level templates loaded via `renderClasspathTemplate` and partials loaded via
    *     `renderClasspathPartial`), for example `model/models.ssp`
    *   - the root-prefixed form (`render`/`include` targets resolved through the resource loader), for example
    *     `python/src/db/model/models.ssp`
    *
    * @param engineFactory
    *   builds a template engine for a given template root, configured identically to the runtime engine (same preamble
    *   injection, bindings and `packagePrefix`)
    * @param roots
    *   the template roots to precompile
    * @param outputDirectory
    *   directory that receives the compiled `.class` files
    * @param generatedSourceDirectory
    *   directory that receives the intermediate generated `.scala` sources
    * @param log
    *   sink for progress messages
    */
  /** Derives the stable `packagePrefix` applied to every generated template class for a template root.
    *
    * The prefix namespaces precompiled classes per template root so that templates sharing a relative path across
    * different roots (for example `fragments/naming/...` under both the SQL and HTTP template trees) never collide on
    * the published classpath. Both the runtime engine and this precompiler must apply the same prefix so the runtime
    * `TemplateEngine.load` resolves the precompiled class by name.
    *
    * Segments are sanitized to valid Scala identifier characters and any segment beginning with a digit is prefixed
    * with `_`.
    */
  def packagePrefix(templateRoot: String): String = {
    val segments = normalizeRoot(templateRoot).split("/").toSeq.filter(_.nonEmpty).map(sanitizeSegment)
    (Seq("scalate", "precompiled") ++ segments).mkString(".")
  }

  private def sanitizeSegment(segment: String): String = {
    val cleaned = segment.map(character => if (character.isLetterOrDigit || character == '_') character else '_')
    if (cleaned.isEmpty || cleaned.charAt(0).isDigit) {
      s"_$cleaned"
    } else {
      cleaned
    }
  }

  def precompile(
      engineFactory: String => TemplateEngine,
      roots: Seq[PrecompileRoot],
      outputDirectory: File,
      generatedSourceDirectory: File,
      log: String => Unit
  ): Unit = {
    val generatedSources =
      roots.flatMap(root => generateRootSources(engineFactory, root, generatedSourceDirectory, log))

    if (generatedSources.isEmpty) {
      log("No SSP templates found to precompile.")
    } else {
      compileAll(generatedSources, outputDirectory, log)
    }
  }

  private def generateRootSources(
      engineFactory: String => TemplateEngine,
      root: PrecompileRoot,
      generatedSourceDirectory: File,
      log: String => Unit
  ): Seq[File] = {
    val engine         = engineFactory(root.templateRoot)
    val normalizedRoot = normalizeRoot(root.templateRoot)
    val templateFiles  = sspFiles(root.sourceDirectory)

    templateFiles.flatMap { templateFile =>
      val relativeUri = relativeTemplateUri(root.sourceDirectory, templateFile)
      if (isInjectedPreamble(relativeUri)) {
        Seq.empty
      } else {
        val templateUris = Seq(relativeUri, s"$normalizedRoot/$relativeUri")
        templateUris.map(uri => generateSource(engine, uri, generatedSourceDirectory, log))
      }
    }
  }

  private def generateSource(
      engine: TemplateEngine,
      templateUri: String,
      generatedSourceDirectory: File,
      log: String => Unit
  ): File = {
    val code   = engine.generateScala(templateUri)
    val target = new File(generatedSourceDirectory, code.className.replace('.', '/') + ".scala")
    val _      = Option(target.getParentFile).map(_.mkdirs())
    val _      = Files.write(target.toPath, code.source.getBytes(StandardCharsets.UTF_8))
    log(s"Generated ${code.className} from $templateUri")
    target
  }

  private def compileAll(sources: Seq[File], outputDirectory: File, log: String => Unit): Unit = {
    val _         = outputDirectory.mkdirs()
    val classpath = System.getProperty("java.class.path")
    // Compile generated template sources with the same options the runtime engine uses (see templateScalacOptions) so
    // the build-time and runtime compilers behave identically. `-no-indent` removes the otherwise-pervasive
    // optional-braces indentation warnings without suppressing genuine diagnostics.
    val arguments =
      Array("-classpath", classpath) ++ templateScalacOptions ++
        Array("-d", outputDirectory.getAbsolutePath) ++ sources.map(_.getAbsolutePath)
    log(s"Compiling ${sources.size.toString} generated template sources into ${outputDirectory.getAbsolutePath}")
    val reporter  = dotty.tools.dotc.Main.process(arguments)
    if (reporter.hasErrors) {
      throw new RuntimeException(s"Scalate template precompilation failed:\n${reporter.summary}")
    }
  }

  private def sspFiles(directory: File): Seq[File] =
    if (!directory.isDirectory) {
      Seq.empty
    } else {
      val entries = Option(directory.listFiles()).map(_.toSeq).getOrElse(Seq.empty)
      entries.flatMap { entry =>
        if (entry.isDirectory) {
          sspFiles(entry)
        } else if (entry.getName.endsWith(".ssp")) {
          Seq(entry)
        } else {
          Seq.empty
        }
      }
    }

  private def relativeTemplateUri(rootDirectory: File, file: File): String =
    rootDirectory.toURI.relativize(file.toURI).getPath

  private def isInjectedPreamble(relativeUri: String): Boolean =
    relativeUri == "preamble.ssp" || relativeUri.endsWith("/preamble.ssp")

  private def normalizeRoot(templateRoot: String): String =
    templateRoot.stripPrefix("classpath:").stripPrefix("/").stripSuffix("/")
}
