package com.jacoby6000.smithplates.http.transform

import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.transform.ModelTransformer

import scala.jdk.CollectionConverters.*

/** Adds `@restJson1` to `@httpService` services so Smithy OpenAPI export can infer a protocol. */
object ApplyHttpServiceRestJson1ModelTransformer {
  def transform(model: Model): Model = {
    val replacements =
      model
        .shapes(classOf[ServiceShape])
        .iterator()
        .asScala
        .flatMap(internal.transformService)
        .toList

    if (replacements.isEmpty) {
      model
    } else {
      ModelTransformer.create().replaceShapes(model, replacements.asJava)
    }
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def transformService(service: ServiceShape): Option[ServiceShape] =
      service.httpService match {
        case None                                                           =>
          None
        case Some(_) if service.getTrait(classOf[RestJson1Trait]).isPresent =>
          None
        case Some(_)                                                        =>
          Some(
            service
              .toBuilder()
              .addTrait(
                RestJson1Trait
                  .builder()
                  .sourceLocation(service.getSourceLocation)
                  .build()
              )
              .build()
          )
      }
  }
}
