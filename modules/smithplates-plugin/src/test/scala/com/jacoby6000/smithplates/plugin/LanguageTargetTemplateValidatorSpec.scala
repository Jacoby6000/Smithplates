package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts

class LanguageTargetTemplateValidatorSpec extends munit.FunSuite {
  private val PythonDbTemplateDirectory = "classpath:python/src/db"

  test("required template list excludes bundled verbatim resources") {
    val allTemplates =
      SqlServiceCodegenDbArtifacts
        .forEnabledDialects(PythonDbTemplateDirectory, List("sqlite", "postgres"))
        .flatMap(SqlServiceCodegenDbArtifacts.templatePath)
        .distinct

    val renderedTemplates = allTemplates.filter(SqlServiceCodegenDbArtifacts.isRenderedTemplate)
    val verbatimResources = allTemplates.filterNot(SqlServiceCodegenDbArtifacts.isRenderedTemplate)

    assert(verbatimResources.nonEmpty, clue = "expected bundled verbatim resources in the deck")
    verbatimResources.foreach { verbatimPath =>
      assert(
        !renderedTemplates.contains(verbatimPath),
        clue = s"verbatim resource $verbatimPath must not be validated as an SSP template"
      )
    }
  }

  test("validateRequiredTemplatesExist succeeds for bundled python sqlite and postgres decks") {
    LanguageTargetTemplateValidator.internal
      .validateRequiredTemplatesExist(
        languageId = "python",
        templateDirectory = LanguageTargetTemplateValidator.defaultTemplateDirectory("python"),
        enabledDialectKeys = List("sqlite", "postgres")
      )
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        _ => ()
      )
  }
}
