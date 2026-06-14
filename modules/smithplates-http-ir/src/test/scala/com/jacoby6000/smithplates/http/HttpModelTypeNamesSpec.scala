package com.jacoby6000.smithplates.http

import munit.FunSuite

class HttpModelTypeNamesSpec extends FunSuite {
  test("HttpModelTypeNames collects nested structure and union references from member types") {
    val structureNames = Set("PostalAddress", "ImageAsset", "CreateContentInput")
    val unionNames     = Set("MediaAttachment")
    assertEquals(
      HttpModelTypeNames.referencedModelTypeNames("MediaAttachment", structureNames, unionNames),
      List("MediaAttachment")
    )
    assertEquals(
      HttpModelTypeNames.referencedModelTypeNames("List[ImageAsset]", structureNames, unionNames),
      List("ImageAsset")
    )
    assertEquals(
      HttpModelTypeNames.referencedModelTypeNames("String", structureNames, unionNames),
      Nil
    )
  }
}
