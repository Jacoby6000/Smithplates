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
}
