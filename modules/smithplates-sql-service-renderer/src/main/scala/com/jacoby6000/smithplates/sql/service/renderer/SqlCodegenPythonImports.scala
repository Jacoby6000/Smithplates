package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*

object SqlCodegenPythonNaming {
  def serviceModuleBaseName(serviceName: String): String =
    serviceName
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .toLowerCase

  def serviceProtocolName(serviceName: String): String =
    s"${serviceName}ServiceProtocol"

  def transactionRunModuleName(dialectKey: String): String =
    dialectKey match {
      case "sqlite"   => "sqlite_transaction_run"
      case "postgres" => "psycopg_transaction_run"
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }
}

object SqlCodegenPythonImports {
  import SqlCodegenPythonNaming.*

  def enumTypeNameSet(context: SqlCodegenServiceContext): Set[String] =
    (context.stringEnums.map(_.name) ++ context.intEnums.map(_.name)).toSet

  def enumImportBlock(context: SqlCodegenServiceContext): String = {
    val enumNames = referencedEnumTypeNames(context).toList.sorted
    if (enumNames.isEmpty) {
      ""
    } else {
      enumNames
        .map(enumName =>
          s"from ${qualifiedModule(context.packageName, SqlCodegenSnakeCase.toSnakeCase(enumName))} import $enumName")
        .mkString("\n", "\n", "\n")
    }
  }

  def referencedEnumTypeNames(context: SqlCodegenServiceContext): Set[String] = {
    val enumNames  = enumTypeNameSet(context)
    val referenced = scala.collection.mutable.LinkedHashSet.empty[String]

    def reference(typeName: String): Unit =
      referencedTypeNames(typeName).foreach { candidate =>
        if (enumNames.contains(candidate)) {
          referenced += candidate
        }
      }

    context.models.flatMap(_.members).foreach(member => reference(member.typeName))
    context.unions.flatMap(_.members).foreach(member => reference(member.typeName))
    context.operations.foreach { operation =>
      operation.parameters.foreach(parameter => reference(parameter.typeName))
      operation.outputTypeName.foreach(reference)
      operation.sql.foreach { sql =>
        sql.bindParameters.foreach(bind => reference(bind.typeName))
        sql.resultFields.foreach(field => reference(field.typeName))
        sql.selectOneOutput.foreach { output =>
          output.nestedBindings.foreach { nested =>
            nested.fields.foreach(field => reference(field.typeName))
          }
        }
      }
    }

    referenced.toSet
  }

  def qualifiedModule(packageName: String, relativeModulePath: String): String = {
    val suffix = relativeModulePath.stripPrefix("/").replace('/', '.')
    s"$packageName.$suffix"
  }

  def protocolTableModelImportBlock(context: SqlCodegenServiceContext): String = {
    val (tableModels, unions) = protocolReferencedNames(context)
    renderModelsImport(context.packageName, serviceModuleBaseName(context.name), tableModels, unions).getOrElse("")
  }

  def serviceLocalImportBlock(context: SqlCodegenServiceContext): String = {
    val moduleBase            = serviceModuleBaseName(context.name)
    val (tableModels, unions) = serviceReferencedNames(context)
    val operationResultNames  = serviceReferencedOperationResultNames(context)

    val blocks = List.newBuilder[String]
    renderModelsImport(context.packageName, moduleBase, tableModels, unions).foreach(blocks += _)
    renderProtocolImport(context.packageName, moduleBase, operationResultNames, context.name).foreach(blocks += _)
    Option(enumImportBlock(context)).filter(_.nonEmpty).foreach(blocks += _)
    if (context.hasSqlOperations) {
      blocks += s"from ${qualifiedModule(context.packageName, s"${context.dialectKey}/${transactionRunModuleName(context.dialectKey)}")} import run"
    }

    blocks.result().sortBy(extractImportModuleName).mkString("\n")
  }

  def protocolReferencedNames(context: SqlCodegenServiceContext): (List[String], List[String]) = {
    val tableModelNames = tableModelNameSet(context)
    val unionNames      = unionNameSet(context)
    val referencedTable = scala.collection.mutable.LinkedHashSet.empty[String]
    val referencedUnion = scala.collection.mutable.LinkedHashSet.empty[String]

    def reference(typeName: String): Unit =
      referencedTypeNames(typeName).foreach { candidate =>
        if (tableModelNames.contains(candidate)) {
          referencedTable += candidate
        }
        if (unionNames.contains(candidate)) {
          referencedUnion += candidate
        }
      }

    context.operations.foreach { operation =>
      operation.parameters.foreach(parameter => reference(parameter.typeName))
      operation.outputTypeName.foreach(reference)
    }

    context.models
      .filter(_.namespace == SqlSelectOneDerivedOutputBuilder.DerivedNamespace)
      .flatMap(_.members)
      .foreach(member => reference(member.typeName))

    (referencedTable.toList.sorted, referencedUnion.toList.sorted)
  }

  def serviceReferencedNames(context: SqlCodegenServiceContext): (List[String], List[String]) = {
    val tableModelNames = tableModelNameSet(context)
    val unionNames      = unionNameSet(context)
    val referencedTable = scala.collection.mutable.LinkedHashSet.empty[String]
    val referencedUnion = scala.collection.mutable.LinkedHashSet.empty[String]

    def reference(typeName: String): Unit =
      referencedTypeNames(typeName).foreach { candidate =>
        if (tableModelNames.contains(candidate)) {
          referencedTable += candidate
        }
        if (unionNames.contains(candidate)) {
          referencedUnion += candidate
        }
      }

    context.operations.filter(_.sql.isDefined).foreach { operation =>
      operation.parameters.foreach(parameter => reference(parameter.typeName))
      operation.outputTypeName.foreach(reference)
      operation.sql.foreach { sql =>
        sql.bindParameters.foreach { bindParameter =>
          reference(bindParameter.typeName)
          bindParameter.jsonTypeName.foreach(reference)
        }
        sql.resultFields.foreach(resultField => reference(resultField.typeName))
        sql.selectOneOutput.foreach { output =>
          output.nestedBindings.foreach { nested =>
            reference(nested.shapeName)
            // JSON columns on join tables produce `_read_<Type>` helpers that reference the model class.
            nested.fields.foreach(field => reference(field.typeName))
          }
        }
      }
    }

    (referencedTable.toList.sorted, referencedUnion.toList.sorted)
  }

  private def serviceReferencedOperationResultNames(context: SqlCodegenServiceContext): List[String] = {
    val derivedNames =
      context.models
        .filter(_.namespace == SqlSelectOneDerivedOutputBuilder.DerivedNamespace)
        .map(_.name)
        .toSet
    context.operations
      .filter(_.sql.isDefined)
      .flatMap(_.outputTypeName)
      .filter(derivedNames.contains)
      .distinct
      .sorted
      .toList
  }

  private def tableModelNameSet(context: SqlCodegenServiceContext): Set[String] =
    context.models
      .filter(_.namespace != SqlSelectOneDerivedOutputBuilder.DerivedNamespace)
      .map(_.name)
      .toSet

  private def unionNameSet(context: SqlCodegenServiceContext): Set[String] =
    context.unions.map(_.name).toSet

  private def extractImportModuleName(block: String): String =
    block.linesIterator.next().stripPrefix("from ").takeWhile(_ != ' ')

  private def renderModelsImport(
      packageName: String,
      moduleBase: String,
      tableModels: List[String],
      unions: List[String]
  ): Option[String] = {
    val names = (tableModels ++ unions).sorted
    if (names.isEmpty) {
      None
    } else {
      val body = names.map(name => s"    $name,").mkString("\n", "\n", "\n")
      Some(s"from ${qualifiedModule(packageName, s"models/${moduleBase}_models")} import ($body)")
    }
  }

  def referencedTypeNames(typeName: String): List[String] = {
    val listPattern = """(?i)list\[(.+)]""".r
    typeName match {
      case listPattern(inner) => List(inner.trim)
      case other              => List(other)
    }
  }

  def integrationTestLocalImportBlock(
      packageName: String,
      serviceName: String,
      dialectKey: String,
      testImports: String
  ): String = {
    val implementationModule =
      s"${serviceModuleBaseName(serviceName)}${dialectKey match {
          case "sqlite"   => "_aiosqlite"
          case "postgres" => "_psycopg"
          case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
        }}"
    val implementationClass  =
      dialectKey match {
        case "sqlite"   => s"${serviceName}AiosqliteService"
        case "postgres" => s"${serviceName}PsycopgService"
        case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
      }
    val implementationBlock  =
      s"from ${qualifiedModule(packageName, s"$dialectKey/$implementationModule")} import $implementationClass"
    val blocks               = importBlocks(testImports) :+ implementationBlock
    blocks
      .groupBy(extractImportModuleName)
      .values
      .map(_.head)
      .toList
      .sortBy(extractImportModuleName)
      .mkString("\n", "\n", "")
  }

  private def importBlocks(importText: String): List[String] =
    if (importText.isEmpty) {
      Nil
    } else {
      val blocks  = List.newBuilder[String]
      var current = List.newBuilder[String]
      importText.linesIterator.foreach { line =>
        if (line.startsWith("from ") && current.result().nonEmpty) {
          blocks += current.result().mkString("\n")
          current = List.newBuilder[String]
        }
        if (line.nonEmpty) {
          current += line
        }
      }
      if (current.result().nonEmpty) {
        blocks += current.result().mkString("\n")
      }
      blocks.result()
    }

  private def renderProtocolImport(
      packageName: String,
      moduleBase: String,
      resultNames: List[String],
      serviceName: String
  ): Option[String] = {
    val protocolName = serviceProtocolName(serviceName)
    if (resultNames.isEmpty) {
      Some(s"from ${qualifiedModule(packageName, s"${moduleBase}_protocol")} import $protocolName")
    } else {
      val body =
        (resultNames.map(name => s"    $name,") :+ s"    $protocolName,").mkString("\n", "\n", "\n")
      Some(s"from ${qualifiedModule(packageName, s"${moduleBase}_protocol")} import ($body)")
    }
  }
}
