package com.jacoby6000.smithplates.codegen.core.strategy.config

import munit.FunSuite

class LanguageBaseConfigLoaderSpec extends FunSuite {
  test("loadJson rejects invalid JSON") {
    LanguageBaseConfigLoader.loadJson("{") match {
      case cats.data.Validated.Invalid(errors) =>
        assert(errors.head.message.startsWith("Invalid language base config: invalid JSON:"))
      case cats.data.Validated.Valid(_)        =>
        fail("expected invalid JSON to fail")
    }
  }

  test("loadJson rejects missing namingStrategy") {
    LanguageBaseConfigLoader.loadJson("""{"typeSyntax": {}}""") match {
      case cats.data.Validated.Invalid(_) =>
        ()
      case cats.data.Validated.Valid(_)   =>
        fail("expected incomplete base config to fail")
    }
  }

  test("loadJson rejects unsupported naming style") {
    val json =
      """
        |{
        |  "namingStrategy": {
        |    "fileNames": { "style": "kebab-case" },
        |    "packageSeparator": ".",
        |    "classNames": { "style": "unchanged" },
        |    "packageNames": { "style": "unchanged" },
        |    "valueNames": { "style": "snake_case" },
        |    "constantNames": { "style": "screaming_snake_case" },
        |    "functionNames": { "style": "snake_case" }
        |  },
        |  "typeSyntax": {
        |    "primitives": { "string": "str" },
        |    "timestamp": { "dateTime": "datetime" },
        |    "optional": "{inner} | None",
        |    "list": "list[{element}]",
        |    "map": "dict[{key}, {value}]",
        |    "modelRef": "{name}"
        |  }
        |}
        |""".stripMargin

    LanguageBaseConfigLoader.loadJson(json) match {
      case cats.data.Validated.Invalid(errors) =>
        assert(errors.exists(_.message.contains("unsupported style 'kebab-case'")))
      case cats.data.Validated.Valid(_)        =>
        fail("expected unsupported naming style to fail")
    }
  }
}
