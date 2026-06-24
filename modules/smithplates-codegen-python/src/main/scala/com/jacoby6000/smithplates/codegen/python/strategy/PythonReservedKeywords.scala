package com.jacoby6000.smithplates.codegen.python.strategy

/** Python reserved identifiers remapped with a trailing underscore (PEP 8). */
object PythonReservedKeywords {
  val remaps: Map[String, String] =
    List(
      "False",
      "None",
      "True",
      "and",
      "as",
      "assert",
      "async",
      "await",
      "break",
      "class",
      "continue",
      "def",
      "del",
      "elif",
      "else",
      "except",
      "finally",
      "for",
      "from",
      "global",
      "if",
      "import",
      "in",
      "is",
      "lambda",
      "nonlocal",
      "not",
      "or",
      "pass",
      "raise",
      "return",
      "try",
      "while",
      "with",
      "yield",
      "id"
    ).map(keyword => keyword -> s"${keyword}_").toMap
}
