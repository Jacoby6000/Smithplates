package com.jacoby6000.smithplates.sql.service.traits;

import java.util.Objects;
import java.util.Optional;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

public final class SqlDeriveSelectFromValue {
    private static final String TABLE = "table";
    private static final String ALIAS = "alias";

    private final String table;
    private final String alias;

    public SqlDeriveSelectFromValue(String table, String alias) {
        this.table = Objects.requireNonNull(table, TABLE);
        this.alias = alias;
    }

    public String getTable() {
        return table;
    }

    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }

    public static SqlDeriveSelectFromValue fromNode(Node node) {
        ObjectNode objectNode = node.expectObjectNode();
        String table = objectNode.expectStringMember(TABLE).getValue();
        String alias = objectNode.getStringMember(ALIAS).map(n -> n.getValue()).orElse(null);
        return new SqlDeriveSelectFromValue(table, alias);
    }

    public Node toNode() {
        ObjectNode.Builder builder = Node.objectNodeBuilder().withMember(TABLE, table);
        if (alias != null) {
            builder.withMember(ALIAS, alias);
        }
        return builder.build();
    }
}
