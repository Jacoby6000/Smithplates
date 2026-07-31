package com.jacoby6000.smithplates.codegen.core.strategy

import com.jacoby6000.smithplates.codegen.core.ModelId

/** Target-language naming helpers derived from a [[NamingStrategy]]. */
trait Conventions {
  def className(id: ModelId): String
  def modulePath(id: ModelId): String
  def fileName(id: ModelId): String
  def fileStem(id: ModelId): String
  def memberName(smithyName: String): String
  def functionName(smithyName: String): String
  def constantName(smithyName: String): String
  def packageName(smithyNamespace: String): String
  def rootNamespaceDir: String
  def packageSeparator: String
}

object Conventions {
  def fromStrategy(strategy: NamingStrategy, rootNamespace: Option[String] = None): Conventions =
    new DefaultConventions(strategy, rootNamespace)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def applyCase(style: NamingConventionStyle, value: String): String =
      style match {
        case NamingConventionStyle.SnakeCase          =>
          value
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .toLowerCase
        case NamingConventionStyle.PascalCase         =>
          if (value.isEmpty) {
            value
          } else {
            val segments =
              value
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .split("_")
                .filter(_.nonEmpty)
            segments.map(segment => s"${segment.head.toUpper}${segment.tail.toLowerCase}").mkString
          }
        case NamingConventionStyle.CamelCase          =>
          val pascal = applyCase(NamingConventionStyle.PascalCase, value)
          if (pascal.isEmpty) {
            pascal
          } else {
            s"${pascal.head.toLower}${pascal.tail}"
          }
        case NamingConventionStyle.ScreamingSnakeCase =>
          applyCase(NamingConventionStyle.SnakeCase, value).toUpperCase
        case NamingConventionStyle.Unchanged          =>
          value
      }

    def formatIdentifier(strategy: NamingStrategy, convention: NamingConvention, value: String): String = {
      val cased         =
        applyCase(convention.style, value) + convention.suffix
      val remappedChars =
        strategy.illegalCharRemaps.foldLeft(cased) { case (current, (from, to)) =>
          current.replace(from, to)
        }
      strategy.reservedKeywordRemaps.getOrElse(remappedChars, remappedChars)
    }

    def namespaceSegments(namespace: String): List[String] =
      namespace.split('.').toList.filter(_.nonEmpty)

    def packageSegments(
        strategy: NamingStrategy,
        rootNamespace: Option[String],
        smithyNamespace: String
    ): List[String] =
      rootNamespace.toList.flatMap(namespaceSegments) ++
        namespaceSegments(smithyNamespace).map(segment => formatIdentifier(strategy, strategy.packageNames, segment))
  }

  final private class DefaultConventions(strategy: NamingStrategy, rootNamespace: Option[String]) extends Conventions {
    import internal.*

    def className(id: ModelId): String =
      formatIdentifier(strategy, strategy.classNames, id.name)

    def modulePath(id: ModelId): String = {
      val moduleBaseName =
        formatIdentifier(strategy, strategy.fileNames.copy(suffix = ""), id.name)
      val segments       = packageSegments(strategy, rootNamespace, id.namespace) :+ moduleBaseName
      segments.mkString(strategy.packageSeparator)
    }

    def fileName(id: ModelId): String =
      formatIdentifier(strategy, strategy.fileNames, id.name)

    def fileStem(id: ModelId): String =
      formatIdentifier(strategy, strategy.fileNames.copy(suffix = ""), id.name)

    def memberName(smithyName: String): String =
      formatIdentifier(strategy, strategy.valueNames, smithyName)

    def functionName(smithyName: String): String =
      formatIdentifier(strategy, strategy.functionNames, smithyName)

    def constantName(smithyName: String): String =
      formatIdentifier(strategy, strategy.constantNames, smithyName)

    def packageName(smithyNamespace: String): String =
      packageSegments(strategy, rootNamespace, smithyNamespace).mkString(strategy.packageSeparator)

    def rootNamespaceDir: String =
      rootNamespace.toList.flatMap(namespaceSegments).mkString("/")

    def packageSeparator: String =
      strategy.packageSeparator
  }
}
