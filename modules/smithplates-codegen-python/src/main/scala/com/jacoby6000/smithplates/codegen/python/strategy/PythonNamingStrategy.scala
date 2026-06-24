package com.jacoby6000.smithplates.codegen.python.strategy

import com.jacoby6000.smithplates.codegen.core.strategy.*

/** Bundled Python [[NamingStrategy]] matching current Smithplates template conventions. */
object PythonNamingStrategy {
  val Default: NamingStrategy =
    NamingStrategy(
      fileNames = NamingConvention.SnakeCase.withSuffix(".py"),
      packageSeparator = ".",
      classNames = NamingConvention.Unchanged,
      packageNames = NamingConvention.Unchanged,
      valueNames = NamingConvention.SnakeCase,
      constantNames = NamingConvention.ScreamingSnakeCase,
      functionNames = NamingConvention.SnakeCase,
      reservedKeywordRemaps = PythonReservedKeywords.remaps
    )
}
