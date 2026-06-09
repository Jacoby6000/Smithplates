package com.jacoby6000.smithy.stache.generators

/** Build-time entrypoint for Smithy Stache generator tasks (`generateGoldenTemplatesFor`, etc.). */
object SmithyStacheGenerators {
  def main(args: Array[String]): Unit =
    args.toList match {
      case "golden-templates" :: language +: caseNames if caseNames.nonEmpty =>
        GoldenTemplateOutputGenerator.generate(language, caseNames)
      case _                                                                 =>
        sys.error(
          """Usage: SmithyStacheGenerators <generator> ...
            |  golden-templates <language> <case-name> [<case-name> ...]
            |""".stripMargin
        )
    }
}
