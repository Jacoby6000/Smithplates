package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.CodegenPackageNames
import com.jacoby6000.smithplates.http.HttpModelTypeNames
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.model.HttpIntEnum
import com.jacoby6000.smithplates.http.model.HttpService
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import com.jacoby6000.smithplates.http.model.HttpStringEnum
import com.jacoby6000.smithplates.http.model.HttpStructure
import com.jacoby6000.smithplates.http.model.HttpUnion
import software.amazon.smithy.model.Model

object HttpServiceCodegenRenderer {
  def render(
      model: Model,
      serviceIr: HttpServiceIr,
      settings: HttpServiceCodegenSettings
  ): HttpValidated[List[HttpCodegenArtifact]] = {
    val _ = model
    serviceIr.services
      .traverse { service =>
        val servicePackageName  = HttpCodegenPackageNames.servicePackageName(settings, service)
        val modelsPackageName   = HttpCodegenPackageNames.modelsPackageName(settings, service.shapeId.getNamespace)
        val typePackageNames    = HttpCodegenPackageNames.buildTypePackageNames(service, settings)
        val view                =
          HttpCodegenTemplateView(
            service = service,
            packageName = servicePackageName,
            modelsPackageName = modelsPackageName,
            typePackageNames = typePackageNames
          )
        val configuredArtifacts =
          settings.artifacts
            .traverse { artifactConfig =>
              artifactConfig.scope match {
                case HttpCodegenArtifactScope.Service         =>
                  internal.renderArtifact(settings, artifactConfig, view)
                case HttpCodegenArtifactScope.RouteGroup(tag) =>
                  service.routeGroups.find(_.tag == tag) match {
                    case Some(routeGroup) =>
                      internal.renderArtifact(
                        settings,
                        artifactConfig,
                        view.copy(routeGroup = Some(routeGroup))
                      )
                    case None             => Nil.validNel
                  }
              }
            }
            .map(_.flatten)
        configuredArtifacts.map { artifacts =>
          if (!settings.emitModels) {
            artifacts
          } else {
            val enumNames           = (service.stringEnums.map(_.name) ++ service.intEnums.map(_.name)).toSet
            val structureArtifacts  =
              service.structures.map(structure =>
                internal.renderStructureModelArtifact(model, settings, service, structure, enumNames))
            val unionArtifacts      =
              service.unions.map(union => internal.renderUnionModelArtifact(settings, service, union, enumNames))
            val stringEnumArtifacts =
              service.stringEnums.map(stringEnum => internal.renderStringEnumModelArtifact(settings, stringEnum))
            val intEnumArtifacts    =
              service.intEnums.map(intEnum => internal.renderIntEnumModelArtifact(settings, intEnum))
            artifacts ++ structureArtifacts ++ unionArtifacts ++ stringEnumArtifacts ++ intEnumArtifacts
          }
        }
      }
      .map(_.flatten)
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def renderArtifact(
        settings: HttpServiceCodegenSettings,
        artifactConfig: HttpServiceCodegenArtifactConfig,
        view: HttpCodegenTemplateView
    ): HttpValidated[List[HttpCodegenArtifact]] = {
      val templateSettings =
        settings.copy(templateDirectory = resolvedTemplateDirectory(settings, artifactConfig))
      val templatePath     = resolveTemplatePath(templateSettings, artifactConfig.template)
      val templateRoot     = templateSettings.templateDirectory.stripPrefix("classpath:")
      val content          = ScalateSspTemplateEngine.renderClasspathTemplate(templatePath, view, Some(templateRoot))
      val relativePath     = resolveOutputPath(settings, artifactConfig, view.service, view.packageName)
      List(
        HttpCodegenArtifact(
          relativePath = relativePath,
          content = content,
          kind = artifactConfig.kind
        )
      ).validNel
    }

    def resolveTemplatePath(settings: HttpServiceCodegenSettings, template: String): String = {
      val baseDirectory = settings.templateDirectory.stripPrefix("classpath:")
      val normalized    = if (baseDirectory.endsWith("/")) baseDirectory else s"$baseDirectory/"
      s"classpath:$normalized$template"
    }

    def resolveOutputPath(
        settings: HttpServiceCodegenSettings,
        artifactConfig: HttpServiceCodegenArtifactConfig,
        service: HttpService,
        packageName: String
    ): String = {
      val outputPrefix       = CodegenPackageNames.outputPathPrefix(service.shapeId.getNamespace)
      val renderedOutputFile =
        HttpCodegenTemplateAttributes.renderOutputPath(artifactConfig.outputFile, service, packageName)
      val relativeOutputFile = s"$outputPrefix/$renderedOutputFile"
      val prefixedOutputFile =
        artifactConfig.kind match {
          case HttpServiceCodegenArtifactKind.Src  =>
            settings.sourceOutputDirectory match {
              case Some(sourceOutputDirectory) =>
                s"${normalizeDirectory(sourceOutputDirectory)}/$relativeOutputFile"
              case None                        => relativeOutputFile
            }
          case HttpServiceCodegenArtifactKind.Test =>
            settings.testOutputDirectory match {
              case Some(testOutputDirectory) =>
                s"${normalizeDirectory(testOutputDirectory)}/$relativeOutputFile"
              case None                      => relativeOutputFile
            }
        }
      prefixedOutputFile
    }

    def resolvedTemplateDirectory(
        settings: HttpServiceCodegenSettings,
        artifactConfig: HttpServiceCodegenArtifactConfig
    ): String =
      artifactConfig.templateSource match {
        case HttpCodegenTemplateSource.Service => settings.templateDirectory
        case HttpCodegenTemplateSource.Models  => settings.resolvedModelTemplateDirectory
      }

    def normalizeDirectory(directory: String): String =
      directory.stripSuffix("/")

    def renderStructureModelArtifact(
        model: Model,
        settings: HttpServiceCodegenSettings,
        service: HttpService,
        structure: HttpStructure,
        enumNames: Set[String]
    ): HttpCodegenArtifact = {
      val templateRoot = settings.resolvedModelTemplateDirectory.stripPrefix("classpath:")
      val packageName  = HttpCodegenPackageNames.modelsPackageName(settings, structure.shapeId.getNamespace)
      val view         = HttpStructureModelTemplateAttributes.build(model, service, structure, enumNames, packageName)
      val content      =
        ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
          resolveTemplatePath(
            settings.copy(templateDirectory = settings.resolvedModelTemplateDirectory),
            "structure.ssp"),
          Map(
            "structure"           -> view.structure,
            "structureMembers"    -> view.members,
            "problemBinding"      -> view.problemBinding,
            "packageName"         -> view.packageName,
            "importTypeNames"     -> view.importTypeNames,
            "needsDatetimeImport" -> view.needsDatetimeImport,
            "needsAnyImport"      -> view.needsAnyImport
          ),
          Some(templateRoot)
        )
      val moduleName   = HttpCodegenTemplateAttributes.toSnakeCase(structure.name)
      val relativePath = modelArtifactRelativePath(settings, moduleName, structure.shapeId.getNamespace)
      HttpCodegenArtifact(
        relativePath = relativePath,
        content = content,
        kind = HttpServiceCodegenArtifactKind.Src
      )
    }

    def renderUnionModelArtifact(
        settings: HttpServiceCodegenSettings,
        service: HttpService,
        union: HttpUnion,
        enumNames: Set[String]
    ): HttpCodegenArtifact = {
      val templateRoot    = settings.resolvedModelTemplateDirectory.stripPrefix("classpath:")
      val structureNames  = service.structures.map(_.name).toSet
      val unionNames      = service.unions.map(_.name).toSet
      val importTypeNames = HttpModelTypeNames.unionReferencedTypeNames(union, structureNames, unionNames, enumNames)
      val needsDatetime   = HttpModelTypeNames.unionNeedsDatetimeImport(union.members)
      val packageName     = HttpCodegenPackageNames.modelsPackageName(settings, union.shapeId.getNamespace)
      val content         =
        ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
          resolveTemplatePath(settings.copy(templateDirectory = settings.resolvedModelTemplateDirectory), "union.ssp"),
          Map(
            "union"               -> union,
            "packageName"         -> packageName,
            "importTypeNames"     -> importTypeNames,
            "needsDatetimeImport" -> needsDatetime
          ),
          Some(templateRoot)
        )
      val moduleName      = HttpCodegenTemplateAttributes.toSnakeCase(union.name)
      val relativePath    = modelArtifactRelativePath(settings, moduleName, union.shapeId.getNamespace)
      HttpCodegenArtifact(
        relativePath = relativePath,
        content = content,
        kind = HttpServiceCodegenArtifactKind.Src
      )
    }

    def modelArtifactRelativePath(
        settings: HttpServiceCodegenSettings,
        moduleName: String,
        smithyNamespace: String
    ): String = {
      val outputPrefix = CodegenPackageNames.outputPathPrefix(smithyNamespace)
      settings.sourceOutputDirectory match {
        case Some(sourceOutputDirectory) =>
          s"${normalizeDirectory(sourceOutputDirectory)}/$outputPrefix/$moduleName.py"
        case None                        =>
          s"$outputPrefix/$moduleName.py"
      }
    }

    def renderStringEnumModelArtifact(
        settings: HttpServiceCodegenSettings,
        stringEnum: HttpStringEnum
    ): HttpCodegenArtifact = {
      val templateRoot = settings.resolvedModelTemplateDirectory.stripPrefix("classpath:")
      val content      =
        ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
          resolveTemplatePath(
            settings.copy(templateDirectory = settings.resolvedModelTemplateDirectory),
            "string_enum.ssp"),
          Map("stringEnum" -> stringEnum),
          Some(templateRoot)
        )
      HttpCodegenArtifact(
        relativePath = modelArtifactRelativePath(
          settings,
          HttpCodegenTemplateAttributes.toSnakeCase(stringEnum.name),
          stringEnum.shapeId.getNamespace),
        content = content,
        kind = HttpServiceCodegenArtifactKind.Src
      )
    }

    def renderIntEnumModelArtifact(
        settings: HttpServiceCodegenSettings,
        intEnum: HttpIntEnum
    ): HttpCodegenArtifact = {
      val templateRoot = settings.resolvedModelTemplateDirectory.stripPrefix("classpath:")
      val content      =
        ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
          resolveTemplatePath(
            settings.copy(templateDirectory = settings.resolvedModelTemplateDirectory),
            "int_enum.ssp"),
          Map("intEnum" -> intEnum),
          Some(templateRoot)
        )
      HttpCodegenArtifact(
        relativePath = modelArtifactRelativePath(
          settings,
          HttpCodegenTemplateAttributes.toSnakeCase(intEnum.name),
          intEnum.shapeId.getNamespace),
        content = content,
        kind = HttpServiceCodegenArtifactKind.Src
      )
    }
  }
}
