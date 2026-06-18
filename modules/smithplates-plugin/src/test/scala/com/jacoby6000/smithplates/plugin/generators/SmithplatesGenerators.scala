package com.jacoby6000.smithplates.plugin.generators

/** Build-time entrypoint for Smithy Stache generator tasks (`generateGoldenTemplatesFor`, etc.). */
object SmithplatesGenerators {
  def main(args: Array[String]): Unit =
    args.toList match {
      case "golden-templates" :: language +: caseNames if caseNames.nonEmpty =>
        GoldenTemplateOutputGenerator.generate(language, caseNames)
      case _                                                                 =>
        sys.error(
          """Usage: SmithplatesGenerators <generator> ...
            |  golden-templates <language> <case-name> [<case-name> ...]
            |
            |Requires publishM2 (plugin in ~/.m2), smithy on PATH, and rendered smithy-build.json
            |(run ./scripts/render-template-smithy-build.sh all first, or use generateGoldenTemplatesFor via sbtn after publishM2).
            |""".stripMargin
        )
    }
}
