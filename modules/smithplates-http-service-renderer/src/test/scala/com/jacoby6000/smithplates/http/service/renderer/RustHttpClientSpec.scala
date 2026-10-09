package com.jacoby6000.smithplates.http.service.renderer

import cats.data.Validated
import munit.FunSuite
import software.amazon.smithy.model.Model

class RustHttpClientSpec extends FunSuite {
  import RustHttpClientSpec.internal.*

  test("bundled Rust emits one flat module with grouped models and exact service metadata") {
    val result = HttpServiceCodegenRenderer.render(model(base), settings)
    result match {
      case Validated.Valid(artifacts) =>
        assertEquals(
          artifacts.map(_.relativePath).toSet,
          Set(
            "src/generated/example/mod.rs",
            "src/generated/example/models.rs",
            "src/generated/example/client.rs",
            "src/generated/example/runtime.rs",
            "src/generated/example/http_problem.rs"
          )
        )
        assert(artifacts.find(_.relativePath.endsWith("client.rs")).exists(_.content.contains("example#Catalog")))
      case Validated.Invalid(errors)  => fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  for ((declaration, message) <- List(
                                   "value: Blob"                                 -> "unsupported",
                                   "value: Timestamp"                            -> "unsupported",
                                   "value: BigDecimal"                           -> "unsupported",
                                   "value: BigInteger"                           -> "unsupported",
                                   "value: Float"                                -> "unsupported",
                                   "value: Double"                               -> "unsupported",
                                   "@default(\"default\") value: String"         -> "default",
                                   "@length(min: 1) value: String"               -> "length",
                                   "@httpQueryParams value: StringMap"           -> "httpQueryParams",
                                   "@httpPrefixHeaders(\"X-\") value: StringMap" -> "httpPrefixHeaders",
                                   "@httpPayload value: Blob"                    -> "unsupported",
                                   "value: Output"                               -> "recursive"
                                 ))
    test(s"reject $declaration before returning any artifacts") {
      val result = HttpServiceCodegenRenderer.render(
        model(base.replace("structure Output { value: String }", s"structure Output { $declaration }")),
        settings)
      assert(result.isInvalid)
      assert(result.swap.toOption.toList.flatMap(_.toList).exists(_.message.contains(message)))
    }

  test("multiple services require explicit selection") {
    val original = model(base + "\n@httpService service Other { version: \"1\", operations: [Get] }\n")
    val rejected = HttpServiceCodegenRenderer.render(original, settings)
    assert(rejected.swap.toOption.toList.flatMap(_.toList).exists(_.message.contains("exactly one service")))
    assert(
      HttpServiceCodegenRenderer.render(original, settings.copy(serviceFilter = Some(Set("example#Catalog")))).isValid)
    assert(HttpServiceCodegenRenderer.render(original, settings.copy(serviceFilter = Some(Set("missing")))).isInvalid)
  }

  test("colliding Rust members fail closed") {
    val original =
      model(base.replace("structure Output { value: String }", "structure Output { fooBar: String\nfoo_bar: String }"))
    assert(
      HttpServiceCodegenRenderer
        .render(original, settings)
        .swap
        .toOption
        .toList
        .flatMap(_.toList)
        .exists(_.message.contains("duplicate Rust member")))
  }

  test("sync and both variants are not in the bundled deck") {
    intercept[IllegalStateException] {
      HttpClientCodegenApiArtifacts.forEnabledLibraries("classpath:rust/src/http/client", List("reqwest.sync"))
    }
  }

  for ((shape, message) <- List(
                             "@sparse list Extra { member: String }" -> "sparse",
                             "list Extra { member: Extra }"          -> "recursive",
                             "intEnum Extra {\nONE = 1\n}"           -> "unsupported"
                           ))
    test(s"reject reachable $shape") {
      val source   = base.replace("structure Output { value: String }", "structure Output { value: Extra }") + shape
      val rejected = HttpServiceCodegenRenderer.render(model(source), settings)
      assert(rejected.swap.toOption.toList.flatMap(_.toList).exists(_.message.contains(message)))
    }

  test("ambiguous service error statuses fail before rendering") {
    val source = base.replace("operations: [Get]", "operations: [Get], errors: [First, Second]") +
      "\n@error(\"client\") @httpError(404) structure First {}\n@error(\"client\") @httpError(404) structure Second {}\n"
    assert(
      HttpServiceCodegenRenderer
        .render(model(source), settings)
        .swap
        .toOption
        .toList
        .flatMap(_.toList)
        .exists(_.message.contains("uniquely identify")))
  }

  test("flat model names collide even across different namespaces") {
    val source   = base.replace("structure Output { value: String }", "structure Output { value: other#Output }")
    val original = Model
      .assembler()
      .discoverModels()
      .disableValidation()
      .addUnparsedModel("rust-test.smithy", source)
      .addUnparsedModel("other.smithy", "$version: \"2\"\nnamespace other\nstructure Output { value: String }")
      .assemble()
      .unwrap()
    assert(
      HttpServiceCodegenRenderer
        .render(original, settings)
        .swap
        .toOption
        .toList
        .flatMap(_.toList)
        .exists(_.message.contains("duplicate flat model")))
  }

  test("modeled errors cannot collide with the reserved Client variant") {
    val source = base.replace("operations: [Get]", "operations: [Get], errors: [Client]") +
      "\n@error(\"client\") @httpError(404) structure Client {}\n"
    assert(
      HttpServiceCodegenRenderer
        .render(model(source), settings)
        .swap
        .toOption
        .toList
        .flatMap(_.toList)
        .exists(_.message.contains("reserved Client error variant")))
  }

  test("unknown auth schemes fail closed") {
    val source = base.replace("@httpService service", "@httpBearerAuth @auth([httpDigestAuth]) @httpService service")
    assert(HttpServiceCodegenRenderer.render(model(source), settings).isInvalid)
  }

  test("query API key metadata is rendered for required authentication") {
    val source = base.replace(
      "@httpService service",
      "@httpApiKeyAuth(name: \"api_key\", in: \"query\") @auth([httpApiKeyAuth]) @httpService service")
    HttpServiceCodegenRenderer.render(model(source), settings) match {
      case Validated.Valid(artifacts) =>
        val client = artifacts.find(_.relativePath.endsWith("client.rs")).getOrElse(fail("missing client")).content
        assert(client.contains("requires_auth: true"))
        assert(client.contains("location: \"query\""))
        assert(client.contains("name: \"api_key\""))
      case Validated.Invalid(errors)  => fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("verbatim HTTP resources preserve their contents") {
    assertEquals(
      ScalateSspTemplateEngine.readClasspathResource("classpath:rust/src/http/client/mod.rs"),
      "// Generated by smithplates HTTP codegen. Do not edit.\npub mod client;\npub mod http_problem;\npub mod models;\n#[doc(hidden)]\npub mod runtime;\n"
    )
  }
}

object RustHttpClientSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val base = """$version: "2"
      |namespace example
      |use smithplates.codegen.http#httpService
      |@httpService service Catalog { version: "1", operations: [Get] }
      |@tags(["items"])
      |@http(method: "GET", uri: "/items", code: 200)
      |operation Get { output: Output }
      |structure Output { value: String }
      |map StringMap { key: String, value: String }
      |""".stripMargin

    def model(source: String): Model =
      Model
        .assembler()
        .discoverModels()
        .disableValidation()
        .addUnparsedModel("rust-test.smithy", source)
        .assemble()
        .unwrap()

    def settings: HttpServiceCodegenSettings = HttpServiceCodegenSettings(
      templateDirectory = "classpath:rust/src/http/client",
      defaultFrameworkKey = "reqwest",
      enabledFrameworkKeys = List("reqwest"),
      sourceOutputDirectory = Some("src/generated"),
      testOutputDirectory = Some("test"),
      artifacts =
        HttpClientCodegenApiArtifacts.forEnabledLibraries("classpath:rust/src/http/client", List("reqwest")) ++
          HttpServiceCodegenApiArtifacts.sharedModels("classpath:rust/src/http/models"),
      rootNamespace = Some("example"),
      packageNameOverride = None,
      modelsPackageNameOverride = None,
      emitModels = true,
      modelTemplateDirectory = Some("classpath:rust/src/http/models")
    )
  }
}
