package com.jacoby6000.smithplates.plugin

final case class SmithplatesLanguageConfiguration(
    sourceOutputDir: String,
    testOutputDir: String,
    enableExternalTemplates: Boolean,
    sql: Option[LanguageTarget],
    http: Option[HttpLanguageTarget]
)

final case class SmithplatesSqlLanguageTarget(
    target: LanguageTarget,
    sourceOutputDir: String,
    testOutputDir: String,
    enableExternalTemplates: Boolean
)

final case class SmithplatesHttpLanguageTarget(
    target: HttpLanguageTarget,
    sourceOutputDir: String,
    testOutputDir: String,
    enableExternalTemplates: Boolean
)
