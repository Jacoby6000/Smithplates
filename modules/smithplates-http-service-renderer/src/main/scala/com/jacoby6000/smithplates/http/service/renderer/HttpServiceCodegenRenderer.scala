package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.model.HttpServiceIr
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
        val context = HttpCodegenServiceContext.fromService(service, settings.defaultFrameworkKey, settings.packageName)
        settings.artifacts
          .traverse { artifactConfig =>
            artifactConfig.scope match {
              case HttpCodegenArtifactScope.Service         =>
                renderArtifact(settings, artifactConfig, HttpCodegenTemplateView(service = context))
              case HttpCodegenArtifactScope.RouteGroup(tag) =>
                context.routeGroups.find(_.tag == tag) match {
                  case Some(routeGroup) =>
                    renderArtifact(
                      settings,
                      artifactConfig,
                      HttpCodegenTemplateView(service = context, routeGroup = Some(routeGroup))
                    )
                  case None             => Nil.validNel
                }
            }
          }
          .map(_.flatten)
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
    val relativePath = resolveOutputPath(settings, artifactConfig, view.service)
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
      context: HttpCodegenServiceContext
  ): String = {
    val renderedOutputFile = HttpCodegenTemplateAttributes.renderOutputPath(artifactConfig.outputFile, context)
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
}
