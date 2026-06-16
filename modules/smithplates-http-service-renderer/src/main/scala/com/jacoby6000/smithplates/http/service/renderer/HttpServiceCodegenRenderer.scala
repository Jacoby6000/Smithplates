package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
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
        val view                = HttpCodegenTemplateView(service = service, packageName = settings.packageName)
        val configuredArtifacts =
          settings.artifacts
            .traverse { artifactConfig =>
              artifactConfig.scope match {
                case HttpCodegenArtifactScope.Service         =>
                  renderArtifact(settings, artifactConfig, view)
                case HttpCodegenArtifactScope.RouteGroup(tag) =>
                  service.routeGroups.find(_.tag == tag) match {
                    case Some(routeGroup) =>
                      renderArtifact(
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
          val enumNames           = (service.stringEnums.map(_.name) ++ service.intEnums.map(_.name)).toSet
          val structureArtifacts  =
            service.structures.map(structure => renderStructureModelArtifact(settings, service, structure, enumNames))
          val unionArtifacts      =
            service.unions.map(union => renderUnionModelArtifact(settings, service, union, enumNames))
          val stringEnumArtifacts =
            service.stringEnums.map(stringEnum => renderStringEnumModelArtifact(settings, stringEnum))
          val intEnumArtifacts    =
            service.intEnums.map(intEnum => renderIntEnumModelArtifact(settings, intEnum))
          artifacts ++ structureArtifacts ++ unionArtifacts ++ stringEnumArtifacts ++ intEnumArtifacts
        }
      }
      .map(_.flatten)
  }

  private def renderArtifact(
      settings: HttpServiceCodegenSettings,
      artifactConfig: HttpServiceCodegenArtifactConfig,
      view: HttpCodegenTemplateView
  ): HttpValidated[List[HttpCodegenArtifact]] = {
    val templateSettings = artifactConfig.templateDirectoryOverride match {
      case Some(templateDirectory) => settings.copy(templateDirectory = templateDirectory)
      case None                    => settings
    }
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

  private def resolveTemplatePath(settings: HttpServiceCodegenSettings, template: String): String = {
    val baseDirectory = settings.templateDirectory.stripPrefix("classpath:")
    val normalized    = if (baseDirectory.endsWith("/")) baseDirectory else s"$baseDirectory/"
    s"classpath:$normalized$template"
  }

  private def resolveOutputPath(
      settings: HttpServiceCodegenSettings,
      artifactConfig: HttpServiceCodegenArtifactConfig,
      service: com.jacoby6000.smithplates.http.model.HttpService,
      packageName: String
  ): String = {
    val renderedOutputFile =
      HttpCodegenTemplateAttributes.renderOutputPath(artifactConfig.outputFile, service, packageName)
    val prefixedOutputFile =
      artifactConfig.kind match {
        case HttpServiceCodegenArtifactKind.Src  =>
          settings.sourceOutputDirectory match {
            case Some(sourceOutputDirectory) =>
              s"${normalizeDirectory(sourceOutputDirectory)}/$renderedOutputFile"
            case None                        => renderedOutputFile
          }
        case HttpServiceCodegenArtifactKind.Test =>
          settings.testOutputDirectory match {
            case Some(testOutputDirectory) =>
              s"${normalizeDirectory(testOutputDirectory)}/$renderedOutputFile"
            case None                      => renderedOutputFile
          }
      }
    prefixedOutputFile
  }

  private def normalizeDirectory(directory: String): String =
    directory.stripSuffix("/")

  private def renderStructureModelArtifact(
      settings: HttpServiceCodegenSettings,
      service: HttpService,
      structure: HttpStructure,
      enumNames: Set[String]
  ): HttpCodegenArtifact = {
    val templateRoot    = settings.resolvedModelTemplateDirectory.stripPrefix("classpath:")
    val structureNames  = service.structures.map(_.name).toSet
    val unionNames      = service.unions.map(_.name).toSet
    val importTypeNames =
      HttpModelTypeNames.structureReferencedTypeNames(structure, structureNames, unionNames, enumNames)
    val needsDatetime   = HttpModelTypeNames.needsDatetimeImport(structure.members)
    val needsAny        = HttpModelTypeNames.needsAnyImport(structure.members)
    val content         =
      ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
        resolveTemplatePath(
          settings.copy(templateDirectory = settings.resolvedModelTemplateDirectory),
          "structure.ssp"),
        Map(
          "structure"           -> structure,
          "packageName"         -> settings.packageName,
          "importTypeNames"     -> importTypeNames,
          "needsDatetimeImport" -> needsDatetime,
          "needsAnyImport"      -> needsAny
        ),
        Some(templateRoot)
      )
    val moduleName      = HttpCodegenTemplateAttributes.toSnakeCase(structure.name)
    val relativePath    = modelArtifactRelativePath(settings, moduleName)
    HttpCodegenArtifact(
      relativePath = relativePath,
      content = content,
      kind = HttpServiceCodegenArtifactKind.Src
    )
  }

  private def renderUnionModelArtifact(
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
    val content         =
      ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
        resolveTemplatePath(settings.copy(templateDirectory = settings.resolvedModelTemplateDirectory), "union.ssp"),
        Map(
          "union"               -> union,
          "packageName"         -> settings.packageName,
          "importTypeNames"     -> importTypeNames,
          "needsDatetimeImport" -> needsDatetime
        ),
        Some(templateRoot)
      )
    val moduleName      = HttpCodegenTemplateAttributes.toSnakeCase(union.name)
    val relativePath    = modelArtifactRelativePath(settings, moduleName)
    HttpCodegenArtifact(
      relativePath = relativePath,
      content = content,
      kind = HttpServiceCodegenArtifactKind.Src
    )
  }

  private def modelArtifactRelativePath(settings: HttpServiceCodegenSettings, moduleName: String): String =
    settings.sourceOutputDirectory match {
      case Some(sourceOutputDirectory) =>
        s"${normalizeDirectory(sourceOutputDirectory)}/${settings.serviceTypePrefix}/models/$moduleName.py"
      case None                        =>
        s"${settings.serviceTypePrefix}/models/$moduleName.py"
    }

  private def renderStringEnumModelArtifact(
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
      relativePath = modelArtifactRelativePath(settings, HttpCodegenTemplateAttributes.toSnakeCase(stringEnum.name)),
      content = content,
      kind = HttpServiceCodegenArtifactKind.Src
    )
  }

  private def renderIntEnumModelArtifact(
      settings: HttpServiceCodegenSettings,
      intEnum: HttpIntEnum
  ): HttpCodegenArtifact = {
    val templateRoot = settings.resolvedModelTemplateDirectory.stripPrefix("classpath:")
    val content      =
      ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
        resolveTemplatePath(settings.copy(templateDirectory = settings.resolvedModelTemplateDirectory), "int_enum.ssp"),
        Map("intEnum" -> intEnum),
        Some(templateRoot)
      )
    HttpCodegenArtifact(
      relativePath = modelArtifactRelativePath(settings, HttpCodegenTemplateAttributes.toSnakeCase(intEnum.name)),
      content = content,
      kind = HttpServiceCodegenArtifactKind.Src
    )
  }
}
