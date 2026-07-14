package com.jacoby6000.smithplates.codegen.core.planning

import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.TemplateRenderFailed
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.TypeRenderer
import com.jacoby6000.smithplates.codegen.core.strategy.UnconfiguredTypeRenderer

final case class TemplateView[Subject, M](
    subject: Subject,
    usedTypes: List[Model[M]],
    conventions: Conventions,
    typeRenderer: TypeRenderer = UnconfiguredTypeRenderer,
    commentPrefix: String = "#"
)

trait TemplateRenderer {
  def render[S, M](templatePath: String, view: TemplateView[S, M]): CodegenValidated[String]
}

object TemplateRenderer {
  def fromFunction[S, M](renderFn: (String, TemplateView[S, M]) => String): TemplateRenderer =
    new TemplateRenderer {
      def render[S0, M0](templatePath: String, view: TemplateView[S0, M0]): CodegenValidated[String] =
        try
          CodegenValidated.valid(renderFn(templatePath, view.asInstanceOf[TemplateView[S, M]]))
        catch {
          case error: Exception =>
            TemplateRenderFailed(
              templatePath,
              Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            ).invalidNel
        }
    }
}
