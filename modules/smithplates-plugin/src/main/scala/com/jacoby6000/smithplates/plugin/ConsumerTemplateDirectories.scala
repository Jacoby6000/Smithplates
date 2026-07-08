package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.codegen.core.planning.CodegenTemplatePaths

import java.nio.file.Path
import java.nio.file.Paths

/** Normalizes consumer `additionalTemplatesDirectory` values to `classpath:` or `file:` roots. */
object ConsumerTemplateDirectories {
  def isClasspathDirectory(directory: String): Boolean =
    CodegenTemplatePaths.isClasspathQualified(directory)

  def requiresEnableExternalTemplates(directory: String): Boolean =
    !isClasspathDirectory(directory)

  def normalize(directory: String): String =
    if (isClasspathDirectory(directory)) {
      s"classpath:${CodegenTemplatePaths.classpathPath(directory).stripSuffix("/")}"
    } else if (CodegenTemplatePaths.isFileQualified(directory)) {
      s"file:${Paths.get(CodegenTemplatePaths.filePath(directory)).toAbsolutePath.normalize()}"
    } else {
      s"file:${Paths.get(directory).toAbsolutePath.normalize()}"
    }

  def filesystemPath(directory: String): Path =
    Paths.get(CodegenTemplatePaths.filePath(normalize(directory)))
}
