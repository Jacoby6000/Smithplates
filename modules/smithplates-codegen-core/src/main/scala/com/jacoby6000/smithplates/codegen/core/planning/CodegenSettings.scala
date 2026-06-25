package com.jacoby6000.smithplates.codegen.core.planning

trait StaticResourceLoader {
  def loadContent(resourcePath: String): Option[String]
}

object StaticResourceLoader {
  def fromMap(resources: Map[String, String]): StaticResourceLoader =
    new StaticResourceLoader {
      def loadContent(resourcePath: String): Option[String] =
        resources.get(resourcePath)
    }
}

final case class CodegenSettings(
    sourceOutputDirectory: String,
    testOutputDirectory: String,
    conventions: com.jacoby6000.smithplates.codegen.core.strategy.Conventions,
    staticResourceLoader: StaticResourceLoader = StaticResourceLoader.fromMap(Map.empty)
) {
  def outputBaseDirectory(kind: ArtifactKind): String =
    kind match {
      case ArtifactKind.Src  => sourceOutputDirectory
      case ArtifactKind.Test => testOutputDirectory
    }
}
