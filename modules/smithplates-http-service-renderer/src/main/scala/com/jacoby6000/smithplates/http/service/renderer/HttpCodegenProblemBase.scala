package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.http.codegen.HttpMeta

/** Import paths for the bundled RFC 9457 base model emitted beside the `@httpProblem` trait namespace. */
object HttpCodegenProblemBase {
  val SmithyNamespace: String = "smithplates.codegen.http"
  val ModelName: String       = "HttpProblem"
  val ClassName: String       = "HttpProblem"
  val ModuleName: String      = "http_problem"

  def modelId: ModelId =
    ModelId(SmithyNamespace, ModelName)

  /** Python import module for [[ClassName]], independent of `modelsPackageNameOverride`. */
  def importModule(settings: HttpServiceCodegenSettings): String =
    importModuleFromRoot(settings.rootNamespace)

  def importModuleFromRoot(rootNamespace: Option[String]): String =
    modulePathSegments(rootNamespace).mkString(".")

  def importModuleFromConventionsRoot(rootNamespaceDir: String): String =
    modulePathSegmentsFromDir(rootNamespaceDir).mkString(".")

  def importModule[S](ctx: TemplateView[S, HttpMeta]): String =
    importModuleFromConventionsRoot(ctx.conventions.rootNamespaceDir)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def namespaceSegments(namespace: String): List[String] =
      namespace.split('.').toList.filter(_.nonEmpty)
  }

  private def modulePathSegments(rootNamespace: Option[String]): List[String] =
    rootNamespace.toList.flatMap(internal.namespaceSegments) ++
      internal.namespaceSegments(SmithyNamespace) :+
      ModuleName

  private def modulePathSegmentsFromDir(rootNamespaceDir: String): List[String] =
    rootNamespaceDir.split('/').filter(_.nonEmpty).toList ++
      internal.namespaceSegments(SmithyNamespace) :+
      ModuleName
}
