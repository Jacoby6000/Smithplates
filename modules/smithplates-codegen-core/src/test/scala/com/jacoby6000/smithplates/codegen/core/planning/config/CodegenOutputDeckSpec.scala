package com.jacoby6000.smithplates.codegen.core.planning.config

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.ModelKind
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.BindingFilterAtom
import com.jacoby6000.smithplates.codegen.core.planning.BindingGroup
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.codegen.core.planning.SmithyBinding
import munit.FunSuite

class CodegenOutputDeckSpec extends FunSuite {
  private def loadValid(text: String): CodegenOutputDeck =
    CodegenOutputDeck.loadJson(text) match {
      case Validated.Valid(deck)     => deck
      case Validated.Invalid(errors) => fail(errors.toList.map(_.message).mkString("; "))
    }

  test("loadJson decodes a service template output with defaults") {
    val deck = loadValid("""
      {
        "shared": [
          {
            "id": "python.http.server.app_factory",
            "artifactKind": "src",
            "template": "app_factory.ssp",
            "outputPath": "{{smithyNamespaceDir}}/app_factory.py",
            "binding": { "type": "service" }
          }
        ]
      }
    """)

    assertEquals(deck.variants, Map.empty[String, List[CodegenOutput]])
    assertEquals(
      deck.shared,
      List(
        CodegenOutput.CodegenTemplateBindingOutput(
          id = OutputId("python.http.server.app_factory"),
          kind = ArtifactKind.Src,
          templatePath = "app_factory.ssp",
          outputPath = "{{smithyNamespaceDir}}/app_factory.py",
          binding = SmithyBinding.Service,
          overrides = None
        )
      )
    )
  }

  test("loadJson decodes operation and model bindings with filters and groups") {
    val deck = loadValid("""
      {
        "shared": [
          {
            "id": "structure",
            "artifactKind": "test",
            "template": "models/structure.ssp",
            "outputPath": "{{smithyNamespaceDir}}/{{modelFileName}}",
            "binding": { "type": "model", "filters": [{ "kind": "structure" }], "groupBy": "none" },
            "overrides": "python.http.models.structure"
          }
        ],
        "variants": {
          "fastapi": [
            {
              "id": "route",
              "artifactKind": "src",
              "template": "fastapi/route_group_routes.ssp",
              "outputPath": "{{smithyNamespaceDir}}/apis/{{tagName}}_api.py",
              "binding": { "type": "operation", "filters": ["tagged"], "groupBy": "tag" }
            }
          ]
        }
      }
    """)

    assertEquals(
      deck.shared.head,
      CodegenOutput.CodegenTemplateBindingOutput(
        id = OutputId("structure"),
        kind = ArtifactKind.Test,
        templatePath = "models/structure.ssp",
        outputPath = "{{smithyNamespaceDir}}/{{modelFileName}}",
        binding = SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Structure)), BindingGroup.None),
        overrides = Some(OutputId("python.http.models.structure"))
      )
    )
    val variantBindings =
      deck
        .variant("fastapi")
        .map(_.collect { case template: CodegenOutput.CodegenTemplateBindingOutput =>
          template.binding
        })
    assertEquals(
      variantBindings,
      Some(List(SmithyBinding.Operation(List(BindingFilterAtom.Tagged), BindingGroup.Tag)))
    )
  }

  test("loadJson decodes a static output") {
    val deck = loadValid("""
      {
        "shared": [
          {
            "id": "stub",
            "artifactKind": "test",
            "type": "static",
            "filePath": "postgres/postgres.pyi",
            "copyToPath": "{{smithyNamespaceDir}}/postgres/postgres.pyi"
          }
        ]
      }
    """)

    assertEquals(
      deck.shared,
      List(
        CodegenOutput.CodegenStaticOutput(
          id = OutputId("stub"),
          kind = ArtifactKind.Test,
          filePath = "postgres/postgres.pyi",
          copyToPath = "{{smithyNamespaceDir}}/postgres/postgres.pyi",
          overrides = None
        )
      )
    )
  }

  test("forEnabled composes shared outputs with the requested variants") {
    val deck = loadValid("""
      {
        "shared": [
          { "id": "shared", "artifactKind": "src", "template": "a.ssp", "outputPath": "a.py", "binding": { "type": "service" } }
        ],
        "variants": {
          "fastapi": [
            { "id": "fastapi", "artifactKind": "src", "template": "b.ssp", "outputPath": "b.py", "binding": { "type": "service" } }
          ]
        }
      }
    """)

    deck.forEnabled(List("fastapi")) match {
      case Validated.Valid(outputs)  => assertEquals(outputs.map(_.id.value), List("shared", "fastapi"))
      case Validated.Invalid(errors) => fail(errors.toList.map(_.message).mkString("; "))
    }
    deck.forEnabled(Nil) match {
      case Validated.Valid(outputs)  => assertEquals(outputs.map(_.id.value), List("shared"))
      case Validated.Invalid(errors) => fail(errors.toList.map(_.message).mkString("; "))
    }
  }

  test("forEnabled reports an unknown variant") {
    val deck = loadValid("""{ "shared": [], "variants": {} }""")
    deck.forEnabled(List("flask")) match {
      case Validated.Valid(_)        => fail("expected unknown variant to fail")
      case Validated.Invalid(errors) => assert(errors.exists(_.message.contains("unknown deck variant 'flask'")))
    }
  }

  test("loadJson rejects invalid JSON") {
    CodegenOutputDeck.loadJson("{") match {
      case Validated.Invalid(errors) => assert(errors.head.message.contains("invalid JSON"))
      case Validated.Valid(_)        => fail("expected invalid JSON to fail")
    }
  }

  test("loadJson rejects unknown deck keys") {
    CodegenOutputDeck.loadJson("""{ "shared": [], "extra": 1 }""") match {
      case Validated.Invalid(errors) => assert(errors.exists(_.message.contains("unexpected keys: extra")))
      case Validated.Valid(_)        => fail("expected unknown keys to fail")
    }
  }

  test("loadJson rejects unknown output keys") {
    val json =
      """
      {
        "shared": [
          { "id": "x", "artifactKind": "src", "template": "a.ssp", "outputPath": "a.py", "binding": { "type": "service" }, "typo": true }
        ]
      }
      """
    CodegenOutputDeck.loadJson(json) match {
      case Validated.Invalid(errors) => assert(errors.exists(_.message.contains("unexpected keys: typo")))
      case Validated.Valid(_)        => fail("expected unknown output keys to fail")
    }
  }

  test("loadJson rejects an unsupported binding type") {
    val json =
      """
      {
        "shared": [
          { "id": "x", "artifactKind": "src", "template": "a.ssp", "outputPath": "a.py", "binding": { "type": "cluster" } }
        ]
      }
      """
    CodegenOutputDeck.loadJson(json) match {
      case Validated.Invalid(errors) => assert(errors.exists(_.message.contains("unsupported binding type 'cluster'")))
      case Validated.Valid(_)        => fail("expected unsupported binding to fail")
    }
  }

  test("loadJson rejects an unsupported artifactKind") {
    val json =
      """
      {
        "shared": [
          { "id": "x", "artifactKind": "doc", "template": "a.ssp", "outputPath": "a.py", "binding": { "type": "service" } }
        ]
      }
      """
    CodegenOutputDeck.loadJson(json) match {
      case Validated.Invalid(errors) => assert(errors.exists(_.message.contains("unsupported artifactKind 'doc'")))
      case Validated.Valid(_)        => fail("expected unsupported artifactKind to fail")
    }
  }
}
