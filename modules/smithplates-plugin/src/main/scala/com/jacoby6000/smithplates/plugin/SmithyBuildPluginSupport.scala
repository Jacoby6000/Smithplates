package com.jacoby6000.smithplates.plugin

import software.amazon.smithy.build.SmithyBuildPlugin

object SmithyBuildPluginSupport {
  val SmithplatesPluginName = "smithplates"

  val extraPlugins: Map[String, SmithyBuildPlugin] =
    Map(
      SmithplatesPluginName -> new SmithplatesBuildPlugin()
    )
}
