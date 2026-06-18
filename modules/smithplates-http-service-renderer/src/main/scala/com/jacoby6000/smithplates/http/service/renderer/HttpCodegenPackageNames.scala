package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.CodegenPackageNames
import com.jacoby6000.smithplates.http.model.HttpService

object HttpCodegenPackageNames {
  def servicePackageName(settings: HttpServiceCodegenSettings, service: HttpService): String =
    CodegenPackageNames.resolvePackageName(
      settings.rootNamespace,
      service.shapeId.getNamespace,
      settings.packageNameOverride
    )

  def modelsPackageName(settings: HttpServiceCodegenSettings, smithyNamespace: String): String =
    CodegenPackageNames.resolvePackageName(
      settings.rootNamespace,
      smithyNamespace,
      settings.modelsPackageNameOverride
    )

  def buildTypePackageNames(service: HttpService, settings: HttpServiceCodegenSettings): Map[String, String] = {
    val shapeNamespaces =
      service.structures.map(shape => shape.name -> shape.shapeId.getNamespace) ++
        service.unions.map(shape => shape.name -> shape.shapeId.getNamespace) ++
        service.stringEnums.map(shape => shape.name -> shape.shapeId.getNamespace) ++
        service.intEnums.map(shape => shape.name -> shape.shapeId.getNamespace) ++
        service.serviceErrors.map(shape => shape.name -> shape.shapeId.getNamespace)

    shapeNamespaces.map { case (name, namespace) =>
      name -> modelsPackageName(settings, namespace)
    }.toMap
  }
}
