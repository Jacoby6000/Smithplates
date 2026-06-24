package com.jacoby6000.smithplates.codegen.scalate

import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.TemplateRenderFailed
import com.jacoby6000.smithplates.codegen.core.planning.TemplateRenderer
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView

/** Adapts a classpath Scalate render function to the language-neutral [[TemplateRenderer]] interface. */
object ScalateTemplateRenderer {
  def apply(
      renderClasspathTemplate: (String, Map[String, Any], Option[String]) => String,
      templateRoot: Option[String] = None
  ): TemplateRenderer =
    new TemplateRenderer {
      def render[S, M](templatePath: String, view: TemplateView[S, M]): CodegenValidated[String] =
        try
          CodegenValidated.valid(
            renderClasspathTemplate(
              templatePath,
              Map("ctx" -> view),
              templateRoot
            )
          )
        catch {
          case error: Exception =>
            TemplateRenderFailed(
              templatePath,
              Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            ).invalidNel
        }
    }
}
