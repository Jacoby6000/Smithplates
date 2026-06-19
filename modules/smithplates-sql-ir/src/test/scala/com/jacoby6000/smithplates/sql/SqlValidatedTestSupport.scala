package com.jacoby6000.smithplates.sql

import cats.data.NonEmptyList
import com.jacoby6000.smithplates.sql.model.InvalidMemberColumnType
import com.jacoby6000.smithplates.sql.model.SqlSchemaError

object SqlValidatedTestSupport {
  def hasInvalidMemberKind(errors: NonEmptyList[SqlSchemaError], kind: InvalidMemberColumnType.Kind): Boolean =
    errors.exists {
      case InvalidMemberColumnType(_, _, _, errorKind, _) => errorKind == kind
      case _                                              => false
    }
}
