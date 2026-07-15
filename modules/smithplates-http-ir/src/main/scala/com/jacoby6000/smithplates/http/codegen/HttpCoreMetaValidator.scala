package com.jacoby6000.smithplates.http.codegen

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.invalidNel

/** Feature-metadata validation for HTTP core extraction output. */
object HttpCoreMetaValidator {
  given ModelMetaValidator[HttpMeta] = ModelMetaValidator { model =>
    model.meta.feature match {
      case HttpMeta.HttpResponseMeta(statusCode, _, _, _) if statusCode < 100 || statusCode > 599 =>
        InvalidModelMeta(model.id, s"HTTP status code $statusCode is not a valid HTTP status").invalidNel
      case _                                                                                      =>
        CodegenValidated.unit
    }
  }

  given OperationMetaValidator[HttpOperationMeta] = OperationMetaValidator { operation =>
    operation.meta.feature.websocket match {
      case Some(_) if operation.input.isEmpty  =>
        InvalidOperationMeta(
          operation.id,
          "@websocket operations must declare an input shape (client-to-server messages)"
        ).invalidNel
      case Some(_) if operation.output.isEmpty =>
        InvalidOperationMeta(
          operation.id,
          "@websocket operations must declare an output shape (server-to-client messages)"
        ).invalidNel
      case _                                   =>
        CodegenValidated.unit
    }
  }
}
