package com.jacoby6000.smithplates.codegen.core.planning

/** Helpers for codegen template paths carried on [[CodegenOutput]] entries. */
object CodegenTemplatePaths {

  /** True when a template or static resource path is already anchored at an absolute `classpath:` root (as produced
    * when qualifying consumer additional-template outputs).
    */
  def isClasspathQualified(path: String): Boolean =
    path.startsWith("classpath:")
}
