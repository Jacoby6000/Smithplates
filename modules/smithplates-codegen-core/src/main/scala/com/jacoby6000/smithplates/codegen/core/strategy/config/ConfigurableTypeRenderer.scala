package com.jacoby6000.smithplates.codegen.core.strategy.config

import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import com.jacoby6000.smithplates.codegen.core.strategy.RenderContext
import com.jacoby6000.smithplates.codegen.core.strategy.TypeRenderer

/** Renders [[NeutralType]] using declarative syntax templates from a language `base_config.json`. */
final case class ConfigurableTypeRenderer(config: TypeSyntaxConfig) extends TypeRenderer {
  def render(tpe: NeutralType, ctx: RenderContext[?]): String =
    internal.renderInner(tpe, ctx, config)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def renderInner(tpe: NeutralType, ctx: RenderContext[?], config: TypeSyntaxConfig): String =
      tpe match {
        case OptionalT(inner)                         =>
          substitute(config.optional, "inner" -> renderInner(inner, ctx, config))
        case ListT(element)                           =>
          substitute(config.list, "element" -> renderInner(element, ctx, config))
        case MapT(key, value)                         =>
          substitute(
            config.map,
            "key"   -> renderInner(key, ctx, config),
            "value" -> renderInner(value, ctx, config)
          )
        case BooleanT                                 => primitive(config, "boolean")
        case IntegerT                                 => primitive(config, "integer")
        case LongT                                    => primitive(config, "long")
        case BigIntegerT                              => primitive(config, "bigInteger")
        case FloatT                                   => primitive(config, "float")
        case DoubleT                                  => primitive(config, "double")
        case BigDecimalT                              => primitive(config, "bigDecimal")
        case StringT                                  => primitive(config, "string")
        case BytesT                                   => primitive(config, "bytes")
        case DocumentT                                => primitive(config, "document")
        case TimestampT(TimestampFormat.EpochSeconds) =>
          timestamp(config, "epochSeconds")
        case TimestampT(_)                            =>
          timestamp(config, "dateTime")
        case ref: ModelRef                            =>
          ctx.typeResolver.underlying(ref) match {
            case resolved: ModelRef => ctx.conventions.className(resolved.id)
            case other              => renderInner(other, ctx, config)
          }
      }

    def substitute(template: String, placeholders: (String, String)*): String =
      placeholders.foldLeft(template) { case (current, (key, value)) =>
        current.replace(s"{$key}", value)
      }

    private def primitive(config: TypeSyntaxConfig, key: String): String =
      config.primitives.getOrElse(
        key,
        throw new IllegalStateException(s"missing primitive type syntax for '$key'")
      )

    private def timestamp(config: TypeSyntaxConfig, key: String): String =
      config.timestamp.getOrElse(
        key,
        throw new IllegalStateException(s"missing timestamp type syntax for '$key'")
      )
  }
}
