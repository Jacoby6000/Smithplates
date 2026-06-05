package com.jacoby6000.smithy.stache.sql.codegen.python

object SqlCodegenRowReaders {
  def scalarReaderName(pythonTypeName: String): String =
    pythonTypeName match {
      case "str"      => "_read_str"
      case "int"      => "_read_int"
      case "float"    => "_read_float"
      case "bool"     => "_read_bool"
      case "bytes"    => "_read_bytes"
      case "datetime" => "_read_datetime"
      case "Decimal"  => "_read_decimal"
      case other      => other
    }
}
