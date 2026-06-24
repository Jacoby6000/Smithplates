package com.jacoby6000.smithplates.codegen.core.strategy.config

/** Declarative target-language type syntax loaded from a language template `base_config.json`. */
final case class TypeSyntaxConfig(
    primitives: Map[String, String],
    timestamp: Map[String, String],
    optional: String,
    list: String,
    map: String,
    modelRef: String
)
