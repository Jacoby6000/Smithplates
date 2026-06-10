package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import com.jacoby6000.smithplates.http.model.HttpStructure
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
          val structureArtifacts =
            service.structures.map(structure => renderStructureModelArtifact(settings, structure))
          artifacts ++ structureArtifacts
        }
      }
      .map(_.flatten)
  }

  private def renderArtifact(
      settings: HttpServiceCodegenSettings,
      artifactConfig: HttpServiceCodegenArtifactConfig,
      view: HttpCodegenTemplateView
  ): HttpValidated[List[HttpCodegenArtifact]] = {
    val templatePath = resolveTemplatePath(settings, artifactConfig.template)
    val templateRoot = settings.templateDirectory.stripPrefix("classpath:")
    val content      = ScalateSspTemplateEngine.renderClasspathTemplate(templatePath, view, Some(templateRoot))
    val relativePath = resolveOutputPath(settings, artifactConfig, view.service, view.packageName)
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
      structure: HttpStructure
  ): HttpCodegenArtifact = {
    val templateRoot = settings.templateDirectory.stripPrefix("classpath:")
    val content      =
      ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
        resolveTemplatePath(settings, "models/structure.ssp"),
        Map("structure" -> structure),
        Some(templateRoot)
      )
    val moduleName   = HttpCodegenTemplateAttributes.toSnakeCase(structure.name)
    val relativePath =
      settings.sourceOutputDirectory match {
        case Some(sourceOutputDirectory) =>
          s"${normalizeDirectory(sourceOutputDirectory)}/api/models/$moduleName.py"
        case None                        =>
          s"api/models/$moduleName.py"
      }
    HttpCodegenArtifact(
      relativePath = relativePath,
      content = content,
      kind = HttpServiceCodegenArtifactKind.Src
    )
  }
}
