package com.jacoby6000.smithplates.codegen.python.strategy

import com.jacoby6000.smithplates.codegen.core.strategy.Conventions

object PythonConventions {
  def default(rootNamespace: Option[String] = None): Conventions =
    Conventions.fromStrategy(PythonNamingStrategy.Default, rootNamespace)
}
