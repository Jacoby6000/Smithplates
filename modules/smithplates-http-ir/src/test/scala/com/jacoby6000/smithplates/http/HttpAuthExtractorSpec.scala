package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.http.codegen.*
import com.jacoby6000.smithplates.http.model.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait
import software.amazon.smithy.model.traits.HttpBearerAuthTrait
import software.amazon.smithy.model.traits.synthetic.NoAuthTrait

class HttpAuthExtractorSpec extends FunSuite {
  test("extracts configured schemes and ordered inherited, overridden, no-auth, and optional alternatives") {
    val model   = HttpTestModelLoader.assemble("auth.smithy" -> authModel)
    val service = HttpIrExtractor.extractOrThrow(model).services.head

    assertEquals(
      service.authSchemes,
      List(
        HttpAuthScheme.Bearer(HttpBearerAuthTrait.ID),
        HttpAuthScheme.ApiKey(
          HttpApiKeyAuthTrait.ID,
          "X-API-Key",
          HttpApiKeyLocation.Header,
          Some("ApiKey")
        ),
        HttpAuthScheme.Cookie(ShapeId.from("smithplates.codegen.http#httpCookieAuth"), "session")
      )
    )
    assertEquals(operation(service, "Inherited").authAlternatives.map(_.schemeId), expectedServiceAuth)
    assertEquals(
      operation(service, "Overridden").authAlternatives.map(_.schemeId),
      List(HttpApiKeyAuthTrait.ID, HttpBearerAuthTrait.ID)
    )
    assertEquals(operation(service, "Public").authAlternatives, List(HttpAuthAlternative.NoAuth))
    assertEquals(
      operation(service, "Optional").authAlternatives.map(_.schemeId),
      expectedServiceAuth :+ NoAuthTrait.ID
    )
  }

  test("neutral HTTP metadata preserves auth definitions and effective alternatives") {
    val model = HttpTestModelLoader.assemble("auth.smithy" -> authModel)

    HttpCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (_, services) =>
          val service = services.head
          assertEquals(
            service.meta.feature.authSchemes,
            List(
              HttpAuthSchemeMeta.Bearer(ModelId("smithy.api", "httpBearerAuth")),
              HttpAuthSchemeMeta.ApiKey(
                ModelId("smithy.api", "httpApiKeyAuth"),
                "X-API-Key",
                HttpApiKeyLocationMeta.Header,
                Some("ApiKey")
              ),
              HttpAuthSchemeMeta.Cookie(ModelId("smithplates.codegen.http", "httpCookieAuth"), "session")
            )
          )
          assertEquals(
            service.operations.find(_.id.name == "Optional").toList.flatMap(_.meta.feature.authAlternatives),
            (expectedServiceAuth :+ NoAuthTrait.ID).map(id =>
              HttpAuthAlternativeMeta(ModelId(id.getNamespace, id.getName)))
          )
        }
      )
  }

  test("extracts query API key configuration") {
    val model = HttpTestModelLoader.assemble(
      "query-api-key.smithy" ->
        serviceModel(
          serviceTraits = """@httpApiKeyAuth(name: "api_key", in: "query")
              |@auth([httpApiKeyAuth])""".stripMargin,
          operationTraits = ""
        )
    )

    val service = HttpIrExtractor.extractOrThrow(model).services.head
    assertEquals(
      service.authSchemes,
      List(HttpAuthScheme.ApiKey(HttpApiKeyAuthTrait.ID, "api_key", HttpApiKeyLocation.Query, None))
    )
    assertEquals(
      service.routeGroups.head.operations.head.authAlternatives.map(_.schemeId),
      List(HttpApiKeyAuthTrait.ID))
  }

  test("fails closed for a query API key with an authorization scheme") {
    val model = HttpTestModelLoader.assemble(
      "invalid-query-api-key.smithy" ->
        serviceModel(
          serviceTraits = """@httpApiKeyAuth(name: "api_key", in: "query", scheme: "ApiKey")
                            |@auth([httpApiKeyAuth])""".stripMargin,
          operationTraits = ""
        )
    )

    assertExtractionError(model, "scheme is only supported when in is 'header'")
  }

  test("fails closed for an unknown auth reference with assembler validation disabled") {
    val model = HttpTestModelLoader.assemble(
      "unknown-auth.smithy" ->
        (serviceModel(serviceTraits = "@auth([MissingAuth])", operationTraits = "") +
          "\nstructure MissingAuth {}\n")
    )

    assertExtractionError(model, "not a defined authentication scheme")
  }

  test("fails closed for a defined but unsupported auth scheme") {
    val model = HttpTestModelLoader.assemble(
      "unsupported-auth.smithy" ->
        s"""$$version: "2.0"
           |namespace example
           |
           |use smithy.api#auth
           |use smithy.api#authDefinition
           |use smithy.api#http
           |use smithy.api#tags
           |use smithy.api#trait
           |use smithplates.codegen.http#httpService
           |
           |@trait(selector: "service")
           |@authDefinition
           |structure customAuth {}
           |
           |@httpService
           |@customAuth
           |@auth([customAuth])
           |service ExampleService {
           |    version: "1"
           |    operations: [Get]
           |}
           |
           |${operationDefinition("")}
           |""".stripMargin
    )

    assertExtractionError(model, "authentication scheme 'example#customAuth' is not supported")
  }

  test("fails closed when an operation references a scheme absent from the service catalog") {
    val model = HttpTestModelLoader.assemble(
      "invalid-operation-auth.smithy" ->
        serviceModel(
          serviceTraits = """@httpBearerAuth
                             |@auth([httpBearerAuth])""".stripMargin,
          operationTraits = "@auth([httpApiKeyAuth])"
        )
    )

    assertExtractionError(model, "scheme is not configured on the service")
  }

  test("fails closed when auth conflicts with an operation HTTP binding") {
    val headerCollision = HttpTestModelLoader.assemble(
      "header-collision.smithy" -> collisionModel(
        "@httpBearerAuth\n@auth([httpBearerAuth])",
        "@httpHeader(\"Authorization\")"
      )
    )
    val queryCollision  = HttpTestModelLoader.assemble(
      "query-collision.smithy" -> collisionModel(
        "@httpApiKeyAuth(name: \"api_key\", in: \"query\")\n@auth([httpApiKeyAuth])",
        "@httpQuery(\"api_key\")"
      )
    )

    assertExtractionError(headerCollision, "conflicts with input member bound to header 'Authorization'")
    assertExtractionError(queryCollision, "conflicts with input member bound to query parameter 'api_key'")
  }

  test("allows distinct authentication prefixes on the Authorization header") {
    val model = HttpTestModelLoader.assemble(
      "prefixed-authorization.smithy" ->
        serviceModel(
          serviceTraits = """@httpBearerAuth
                             |@httpApiKeyAuth(name: "Authorization", in: "header", scheme: "ApiKey")
                             |@auth([httpBearerAuth, httpApiKeyAuth])""".stripMargin,
          operationTraits = ""
        )
    )

    assertEquals(
      HttpIrExtractor
        .extractOrThrow(model)
        .services
        .head
        .routeGroups
        .head
        .operations
        .head
        .authAlternatives
        .map(_.schemeId),
      List(HttpBearerAuthTrait.ID, HttpApiKeyAuthTrait.ID)
    )
  }

  def operation(service: HttpService, name: String): HttpOperation =
    service.routeGroups.flatMap(_.operations).find(_.name == name).getOrElse(fail(s"missing operation $name"))

  def assertExtractionError(model: software.amazon.smithy.model.Model, expected: String): Unit =
    HttpIrExtractor
      .extract(model)
      .fold(
        errors =>
          assert(errors.toList.exists(_.message.contains(expected)), errors.toList.map(_.message).mkString("; ")),
        _ => fail(s"expected extraction to fail with '$expected'")
      )

  val expectedServiceAuth: List[ShapeId] =
    List(
      HttpBearerAuthTrait.ID,
      HttpApiKeyAuthTrait.ID,
      ShapeId.from("smithplates.codegen.http#httpCookieAuth")
    )

  val authModel: String =
    s"""$$version: "2.0"
       |namespace example
       |
       |use smithy.api#auth
       |use smithy.api#http
       |use smithy.api#httpApiKeyAuth
       |use smithy.api#httpBearerAuth
       |use smithy.api#optionalAuth
       |use smithy.api#tags
       |use smithplates.codegen.http#httpCookieAuth
       |use smithplates.codegen.http#httpService
       |
       |@httpService
       |@httpBearerAuth
       |@httpApiKeyAuth(name: "X-API-Key", in: "header", scheme: "ApiKey")
       |@httpCookieAuth(name: "session")
       |@auth([httpBearerAuth, httpApiKeyAuth, httpCookieAuth])
       |service ExampleService {
       |    version: "1"
       |    operations: [Inherited, Overridden, Public, Optional]
       |}
       |
       |${operationDefinition("", "Inherited", "/inherited")}
       |
       |${operationDefinition("@auth([httpApiKeyAuth, httpBearerAuth])", "Overridden", "/overridden")}
       |
       |${operationDefinition("@auth([])", "Public", "/public")}
       |
       |${operationDefinition("@optionalAuth", "Optional", "/optional")}
       |""".stripMargin

  def serviceModel(serviceTraits: String, operationTraits: String): String =
    s"""$$version: "2.0"
       |namespace example
       |
       |use smithy.api#auth
       |use smithy.api#http
       |use smithy.api#httpApiKeyAuth
       |use smithy.api#httpBearerAuth
       |use smithy.api#tags
       |use smithplates.codegen.http#httpService
       |
       |@httpService
       |$serviceTraits
       |service ExampleService {
       |    version: "1"
       |    operations: [Get]
       |}
       |
       |${operationDefinition(operationTraits)}
       |""".stripMargin

  def operationDefinition(traits: String, name: String = "Get", uri: String = "/get"): String =
    s"""$traits
       |@tags(["example"])
       |@http(method: "GET", uri: "$uri", code: 200)
       |operation $name {
       |    input: Unit
       |    output: Unit
       |}""".stripMargin

  def collisionModel(serviceTraits: String, memberTrait: String): String =
    s"""$$version: "2.0"
       |namespace example
       |
       |use smithy.api#auth
       |use smithy.api#http
       |use smithy.api#httpApiKeyAuth
       |use smithy.api#httpBearerAuth
       |use smithy.api#httpHeader
       |use smithy.api#httpQuery
       |use smithy.api#tags
       |use smithplates.codegen.http#httpService
       |
       |@httpService
       |$serviceTraits
       |service ExampleService {
       |    version: "1"
       |    operations: [Get]
       |}
       |
       |@tags(["example"])
       |@http(method: "GET", uri: "/get", code: 200)
       |operation Get {
       |    input: CollisionInput
       |    output: Unit
       |}
       |
       |structure CollisionInput {
       |    $memberTrait
       |    value: String
       |}
       |""".stripMargin
}
