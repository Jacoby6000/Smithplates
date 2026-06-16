package com.jacoby6000.smithplates.plugin

final case class SmithplatesLanguageConfiguration(
    sourceOutputDir: String,
    testOutputDir: String,
    sql: Option[LanguageTarget],
    http: Option[HttpLanguageTarget]
)

final case class SmithplatesSqlLanguageTarget(
    target: LanguageTarget,
    sourceOutputDir: String,
    testOutputDir: String
) {
  def enabledDialectKeys: List[String] =
    target.enabledDialectKeys

  def toCodegenSettings(
      languageId: String,
      enabledDialectKeys: List[String],
      queryRenderers: Map[String, com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer],
      schemaDdlRenderers: Map[String, com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer],
      migrationDirectories: Map[String, String]
  ): com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenSettings =
    target.toCodegenSettings(
      languageId = languageId,
      enabledDialectKeys = enabledDialectKeys,
      queryRenderers = queryRenderers,
      schemaDdlRenderers = schemaDdlRenderers,
      migrationDirectories = migrationDirectories,
      sourceOutputDir = sourceOutputDir,
      testOutputDir = testOutputDir
    )
}

final case class SmithplatesHttpLanguageTarget(
    target: HttpLanguageTarget,
    sourceOutputDir: String,
    testOutputDir: String
)
