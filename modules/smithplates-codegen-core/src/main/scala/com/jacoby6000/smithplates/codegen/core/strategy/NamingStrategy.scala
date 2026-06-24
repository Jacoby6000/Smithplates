package com.jacoby6000.smithplates.codegen.core.strategy

/** Declarative per-role naming configuration for a target language. */
final case class NamingStrategy(
    fileNames: NamingConvention,
    packageSeparator: String,
    classNames: NamingConvention,
    packageNames: NamingConvention,
    valueNames: NamingConvention,
    constantNames: NamingConvention,
    functionNames: NamingConvention,
    illegalCharRemaps: Map[String, String] = Map.empty,
    reservedKeywordRemaps: Map[String, String] = Map.empty
)
