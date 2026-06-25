package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts

class LanguageTargetTemplateValidatorSpec extends munit.FunSuite {
  test("required template list excludes bundled static resources") {
    val requiredTemplates =
      SqlServiceCodegenDbArtifacts
        .forEnabledDialects(List("sqlite", "postgres"))
        .flatMap(SqlServiceCodegenDbArtifacts.templatePath)
        .filterNot(SqlServiceCodegenDbArtifacts.bundledTemplatePaths.contains)
        .distinct

    SqlServiceCodegenDbArtifacts.bundledTemplatePaths.foreach { bundledPath =>
      assert(
        !requiredTemplates.contains(bundledPath),
        clue = s"bundled resource $bundledPath must not be validated as an SSP template"
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
