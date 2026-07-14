package com.jacoby6000.smithplates.codegen.core.strategy.config

import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import com.jacoby6000.smithplates.codegen.core.strategy.TypeRenderer

/** Language strategy bundle loaded from `templates/<language>/base_config.json`. */
final case class LanguageBaseConfig(
    namingStrategy: NamingStrategy,
    typeSyntax: TypeSyntaxConfig,
    commentPrefix: String = "#"
) {
  def conventions(rootNamespace: Option[String] = None): Conventions =
    Conventions.fromStrategy(namingStrategy, rootNamespace)

  def typeRenderer: TypeRenderer =
    ConfigurableTypeRenderer(typeSyntax)
}
