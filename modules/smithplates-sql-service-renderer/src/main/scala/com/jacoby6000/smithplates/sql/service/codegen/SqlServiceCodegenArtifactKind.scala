package com.jacoby6000.smithplates.sql.service.codegen

import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig

enum SqlServiceCodegenArtifactKind {
  case Src
  case Test

  def directoryKey: String =
    this match {
      case Src  => "src"
      case Test => "test"
    }
}

object SqlServiceCodegenArtifactKind {
  def fromString(value: String): SqlValidated[SqlServiceCodegenArtifactKind] =
    value.toLowerCase match {
      case "src"  => SqlValidated.valid(SqlServiceCodegenArtifactKind.Src)
      case "test" => SqlValidated.valid(SqlServiceCodegenArtifactKind.Test)
      case other  =>
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"Unsupported sql-service-codegen artifact kind '$other'; supported values are src and test"
          )
        )
    }
}
