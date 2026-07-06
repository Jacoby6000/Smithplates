package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput

/** Composes bundled `@httpService` client artifacts from the `outputs.json` deck resource located beside each
  * language's client templates. The deck data lives entirely in JSON, so this object holds only language-neutral
  * composition logic.
  */
object HttpClientCodegenApiArtifacts {
  def libraryArtifacts(
      clientTemplateDirectory: String,
      libraryKeys: List[String]
  ): CodegenValidated[List[CodegenOutput]] =
    HttpServiceCodegenApiArtifacts.internal.enabled(clientTemplateDirectory, libraryKeys)

  def forEnabledLibraries(
      clientTemplateDirectory: String,
      libraryKeys: List[String]
  ): List[CodegenOutput] =
    HttpServiceCodegenApiArtifacts.internal.orThrow(libraryArtifacts(clientTemplateDirectory, libraryKeys))
}
