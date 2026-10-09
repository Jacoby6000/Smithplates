package com.jacoby6000.smithplates.plugin

import munit.FunSuite
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node

import java.nio.file.Files

class RustResponsePayloadBuildSpec extends FunSuite {
  List("success", "operation error", "service error").foreach { role =>
    test(s"$role payload with headers fails validated plugin boundary before writing artifacts") {
      val serviceErrors = if (role == "service error") ", errors: [Wrapped]" else ""
      val operation     = role match {
        case "success"         => "operation Get { output: Wrapped }"
        case "operation error" => "operation Get { output: Output, errors: [Wrapped] }"
        case _                 => "operation Get { output: Output }"
      }
      val errorTraits   = if (role == "success") "" else "@error(\"client\") @httpError(400)"
      // Smithy's nestedProperties selector permits only operation inputs/outputs, not error structures.
      val payloadTraits = if (role == "success") "@nestedProperties" else ""
      val model         = Model
        .assembler()
        .discoverModels()
        .addUnparsedModel(
          "payload.smithy",
          s"""$$version: "2"
           |namespace example
           |use smithplates.codegen.http#httpService
           |@httpService service Catalog { version: "1", operations: [Get]$serviceErrors }
           |@tags(["items"]) @http(method: "GET", uri: "/items", code: 200)
           |$operation
           |structure Output { value: String }
           |$errorTraits
           |structure Wrapped {
           |    @required @httpHeader("X-Trace") trace: String
           |    @required @httpPayload $payloadTraits body: Payload
           |}
           |structure Payload { @required value: String }
           |""".stripMargin
        )
        .assemble()
        .unwrap()
      val directory     = Files.createTempDirectory("rust-response-payload-")
      val manifest      = FileManifest.create(directory)
      val context       = PluginContext
        .builder()
        .model(model)
        .fileManifest(manifest)
        .settings(
          Node
            .parse("""{
          "rust": { "http": { "client": {}, "outputs": [{"sourceOutputDir":"src/generated","testOutputDir":"test"}] } }
        }""")
            .expectObjectNode()
        )
        .build()
      try {
        val exception = intercept[IllegalArgumentException](new SmithplatesBuildPlugin().execute(context))
        assert(exception.getMessage.contains("response payload wrappers are unsupported"))
        assert(manifest.getFiles.isEmpty)
        val files     = Files.list(directory)
        try assertEquals(files.count(), 0L)
        finally files.close()
      } finally {
        val _ = Files.deleteIfExists(directory)
      }
    }
  }
}
