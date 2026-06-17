package com.jacoby6000.smithplates.plugin

import software.amazon.smithy.build.model.SmithyBuildConfig
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.ModelAssembler

import java.util.ServiceLoader
import scala.jdk.CollectionConverters.*

object SmithyBuildModelSupport {
  private val TraitModelResources =
    List(
      "META-INF/smithy/smithplates.codegen.sql.smithy",
      "META-INF/smithy/smithplates.codegen.sql.service.smithy",
      "META-INF/smithy/smithplates.codegen.http.smithy"
    )

  def modelAssemblerFor(config: SmithyBuildConfig, classLoader: ClassLoader): ModelAssembler = {
    // @sqlDerive* operations use primitive outputs rejected by stock Smithy validation.
    val assembler = Model.assembler(classLoader).discoverModels(classLoader).disableValidation()
    TraitModelResources.foreach { resource =>
      Option(classLoader.getResource(resource)).foreach { url =>
        assembler.addImport(url); ()
      }
    }
    config.getSources.forEach { source =>
      assembler.addImport(source); ()
    }
    config.getImports.forEach { importPath =>
      assembler.addImport(importPath); ()
    }
    assembler
  }

  def loadPlugins(classLoader: ClassLoader): Map[String, software.amazon.smithy.build.SmithyBuildPlugin] = {
    val fromServiceLoader =
      ServiceLoader
        .load(classOf[software.amazon.smithy.build.SmithyBuildPlugin], classLoader)
        .iterator()
        .asScala
        .map(plugin => plugin.getName -> plugin)
        .toMap

    fromServiceLoader ++ SmithyBuildPluginSupport.extraPlugins
  }
}
