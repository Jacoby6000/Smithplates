package com.jacoby6000.smithplates.plugin

import munit.FunSuite
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node

import java.nio.file.Files

class RustProblemCollisionBuildSpec extends FunSuite {
  test("problem jsonName collision fails the validated plugin boundary before writing artifacts") {
    val model     = Model
      .assembler()
      .discoverModels()
      .addUnparsedModel(
        "collision.smithy",
        """$version: "2"
      |namespace example
      |use smithplates.codegen.http#httpService
      |use smithplates.codegen.http#httpProblem
      |@httpService service Catalog { version: "1", operations: [Get], errors: [Problem] }
      |@tags(["items"]) @http(method: "GET", uri: "/items", code: 200)
      |operation Get { output: Output }
      |structure Output { value: String }
      |@error("client") @httpError(400)
      |@httpProblem(type: "https://example.com/problems/test", title: "Test", code: 400)
      |structure Problem { @jsonName("detail") message: String }
      |""".stripMargin
      )
      .assemble()
      .unwrap()
    val directory = Files.createTempDirectory("rust-problem-collision-")
    val manifest  = FileManifest.create(directory)
    val context   = PluginContext
      .builder()
      .model(model)
      .fileManifest(manifest)
      .settings(
        Node
          .parse("""{
      "rust": { "http": { "client": {}, "outputs": [{"sourceOutputDir":"src/generated","testOutputDir":"test"}] } }
    }""").expectObjectNode())
      .build()
    try {
      val exception = intercept[IllegalArgumentException](new SmithplatesBuildPlugin().execute(context))
      assert(exception.getMessage.contains("conflicts with the shared problem"))
      assert(manifest.getFiles.isEmpty)
      val files     = Files.list(directory)
      try assertEquals(files.count(), 0L)
      finally files.close()
    } finally {
      val _ = Files.deleteIfExists(directory)
    }
  }
}
