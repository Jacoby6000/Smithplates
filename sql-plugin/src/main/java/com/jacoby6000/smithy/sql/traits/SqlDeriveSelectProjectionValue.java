package com.jacoby6000.smithy.sql.traits;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

public final class SqlDeriveSelectProjectionValue {
    private static final String ALIAS = "alias";
    private static final String SOURCE = "source";
    private static final String AGGREGATE = "aggregate";

    private final String alias;
    private final String source;
    private final String aggregate;

    public SqlDeriveSelectProjectionValue(String alias, String source, String aggregate) {
        this.alias = Objects.requireNonNull(alias, ALIAS);
        this.source = Objects.requireNonNull(source, SOURCE);
        this.aggregate = aggregate;
    }

    public String getAlias() {
        return alias;
    }

    public String getSource() {
        return source;
    }

    public Optional<String> getAggregate() {
        return Optional.ofNullable(aggregate);
    }

    public static SqlDeriveSelectProjectionValue fromNode(Node node) {
        ObjectNode objectNode = node.expectObjectNode();
        String alias = objectNode.expectStringMember(ALIAS).getValue();
        String source = objectNode.expectStringMember(SOURCE).getValue();
        String aggregate =
                objectNode
                        .getStringMember(AGGREGATE)
                        .map(n -> n.getValue().toLowerCase(Locale.ROOT))
                        .orElse(null);
        return new SqlDeriveSelectProjectionValue(alias, source, aggregate);
    }

    public Node toNode() {
        ObjectNode.Builder builder =
                Node.objectNodeBuilder().withMember(ALIAS, alias).withMember(SOURCE, source);
        if (aggregate != null) {
            builder.withMember(AGGREGATE, aggregate);
        }
        return builder.build();
    }
}
