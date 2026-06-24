package com.jacoby6000.smithplates.codegen.python.strategy

import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import com.jacoby6000.smithplates.codegen.core.strategy.RenderContext
import com.jacoby6000.smithplates.codegen.core.strategy.TypeRenderer

/** Renders [[NeutralType]] as Python 3.10+ type syntax (`list[...]`, `dict[str, ...]`, `X | None`). */
object PythonTypeRenderer extends TypeRenderer {
  def render(tpe: NeutralType, ctx: RenderContext[?]): String =
    internal.renderInner(tpe, ctx)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def renderInner(tpe: NeutralType, ctx: RenderContext[?]): String =
      tpe match {
        case OptionalT(inner)                         => s"${renderInner(inner, ctx)} | None"
        case ListT(element)                           => s"list[${renderInner(element, ctx)}]"
        case MapT(key, value)                         => s"dict[${renderInner(key, ctx)}, ${renderInner(value, ctx)}]"
        case BooleanT                                 => "bool"
        case IntegerT | LongT | BigIntegerT           => "int"
        case FloatT | DoubleT                         => "float"
        case BigDecimalT                              => "Decimal"
        case StringT                                  => "str"
        case BytesT                                   => "bytes"
        case DocumentT                                => "object"
        case TimestampT(TimestampFormat.EpochSeconds) => "float"
        case TimestampT(_)                            => "datetime"
        case ref: ModelRef                            => ref.id.name
      }
  }
}
