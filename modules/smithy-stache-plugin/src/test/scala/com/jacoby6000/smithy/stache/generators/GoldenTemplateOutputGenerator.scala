package com.jacoby6000.smithy.stache.generators

import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateTestDiscovery
import com.jacoby6000.smithy.stache.sql.codegen.SqlServiceCodegenTemplateBackend

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Writes rendered codegen artifacts into `templates/<language>/expected-outputs/<case-name>/`. */
object GoldenTemplateOutputGenerator {
  def generate(language: String, caseNames: Seq[String], repoRoot: Path = defaultRepoRoot): Unit = {
    val classLoader = getClass.getClassLoader
    val backends    = backendsForLanguage(language)
    val discovered  =
      CodegenTemplateTestDiscovery
        .discover(classLoader, backends.map(_.variant).toSet)
        .map(testCase => testCase.name -> testCase)
        .toMap

    caseNames.foreach { caseName =>
      val testCase =
        discovered.getOrElse(
          caseName,
          throw new IllegalArgumentException(
            s"Unknown golden case '$caseName' for language '$language'. " +
              s"Known cases: ${discovered.keys.toList.sorted.mkString(", ")}"
          )
        )

      backends.foreach { backend =>
        val model    = backend.loadModel(testCase).fold(message => sys.error(message), identity)
        val rendered = backend.render(testCase, model).fold(message => sys.error(message), identity)
        rendered.foreach { case (relativePath, content) =>
          val outputPath =
            repoRoot
              .resolve("templates")
              .resolve(language)
              .resolve("expected-outputs")
              .resolve(caseName)
              .resolve(relativePath)
          Files.createDirectories(outputPath.getParent)
          Files.writeString(outputPath, content)
        }
      }
      Console.out.println(s"Wrote golden outputs for '$caseName' ($language)")
    }
  }

  private def defaultRepoRoot: Path =
    Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize

  private def backendsForLanguage(language: String): List[SqlServiceCodegenTemplateBackend.ConfiguredBackend] =
    language match {
      case "python" =>
        List(
          SqlServiceCodegenTemplateBackend.pythonSqlite,
          SqlServiceCodegenTemplateBackend.pythonPostgres
        )
      case other    =>
        sys.error(s"Unsupported language '$other'. Supported languages: python")
    }
}
