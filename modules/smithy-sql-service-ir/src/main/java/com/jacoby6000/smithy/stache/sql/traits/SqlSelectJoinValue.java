package com.jacoby6000.smithy.stache.sql.traits;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

public final class SqlSelectJoinValue {
    private static final String TABLE = "table";
    private static final String TYPE = "type";
    private static final String TABLE_ALIAS = "tableAlias";

    private final String table;
    private final String type;
    private final String tableAlias;

    public SqlSelectJoinValue(String table, String type, String tableAlias) {
        this.table = Objects.requireNonNull(table, TABLE);
        this.type = Objects.requireNonNull(type, TYPE);
        this.tableAlias = tableAlias;
    }

    public String getTable() {
        return table;
    }

    public String getType() {
        return type;
    }

    public Optional<String> getTableAlias() {
        return Optional.ofNullable(tableAlias);
    }

    public static SqlSelectJoinValue fromNode(Node node) {
        ObjectNode objectNode = node.expectObjectNode();
        String table = objectNode.expectStringMember(TABLE).getValue();
        String type =
                objectNode
                        .getStringMember(TYPE)
                        .map(stringNode -> stringNode.getValue().toLowerCase(Locale.ROOT))
                        .orElse("inner");
        String tableAlias =
                objectNode.getStringMember(TABLE_ALIAS).map(n -> n.getValue()).orElse(null);
        return new SqlSelectJoinValue(table, type, tableAlias);
    }

    public Node toNode() {
        ObjectNode.Builder builder =
                Node.objectNodeBuilder().withMember(TABLE, table).withMember(TYPE, type);
        if (tableAlias != null) {
            builder.withMember(TABLE_ALIAS, tableAlias);
        }
        return builder.build();
    }
}
