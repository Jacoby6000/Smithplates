package com.jacoby6000.smithplates.codegen.core.planning

import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.TypeRenderer
import com.jacoby6000.smithplates.codegen.core.strategy.UnconfiguredTypeRenderer

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
    conventions: Conventions,
    typeRenderer: TypeRenderer = UnconfiguredTypeRenderer,
    staticResourceLoader: StaticResourceLoader = StaticResourceLoader.fromMap(Map.empty),
    commentPrefix: String = "#"
) {
  def outputBaseDirectory(kind: ArtifactKind): String =
    kind match {
      case ArtifactKind.Src  => sourceOutputDirectory
      case ArtifactKind.Test => testOutputDirectory
    }
}
