package com.jacoby6000.smithplates.plugin

final case class SmithplatesLanguageConfiguration(
    enableExternalTemplates: Boolean,
    sql: Option[LanguageTarget],
    http: Option[HttpLanguageTarget]
)

final case class SmithplatesSqlLanguageTarget(
    target: LanguageTarget,
    enableExternalTemplates: Boolean
) {
  def withOutputEntryOverrides(
      overrides: SqlServiceOutputEntry
  ): SmithplatesSqlLanguageTarget =
    copy(
      target = target.copy(
        packageName = overrides.packageName.orElse(target.packageName)
      )
    )
}

final case class SmithplatesHttpLanguageTarget(
    target: HttpLanguageTarget,
    enableExternalTemplates: Boolean
) {
  def withOutputEntryOverrides(
      overrides: HttpServiceOutputEntry
  ): SmithplatesHttpLanguageTarget = {
    val overriddenServer =
      target.server.map(server => server.copy(packageName = overrides.packageName.orElse(server.packageName)))
    val overriddenClient =
      target.client.map(client => client.copy(packageName = overrides.packageName.orElse(client.packageName)))
    copy(
      target = target.copy(
        server = overriddenServer,
        client = overriddenClient
      )
    )
  }
}
