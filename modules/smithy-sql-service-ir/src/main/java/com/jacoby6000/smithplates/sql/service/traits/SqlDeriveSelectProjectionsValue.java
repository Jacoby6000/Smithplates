package com.jacoby6000.smithplates.sql.service.traits;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

public final class SqlDeriveSelectProjectionsValue {
    public static final String STAR = "*";

    private final boolean allColumns;
    private final List<SqlDeriveSelectProjectionValue> explicit;

    private SqlDeriveSelectProjectionsValue(
            boolean allColumns, List<SqlDeriveSelectProjectionValue> explicit) {
        this.allColumns = allColumns;
        this.explicit = Collections.unmodifiableList(explicit);
    }

    public static SqlDeriveSelectProjectionsValue allColumns() {
        return new SqlDeriveSelectProjectionsValue(true, List.of());
    }

    public static SqlDeriveSelectProjectionsValue explicit(List<SqlDeriveSelectProjectionValue> projections) {
        return new SqlDeriveSelectProjectionsValue(false, projections);
    }

    public boolean isAllColumns() {
        return allColumns;
    }

    public List<SqlDeriveSelectProjectionValue> getExplicit() {
        return explicit;
    }

    public static SqlDeriveSelectProjectionsValue fromTraitNode(ObjectNode traitNode) {
        return traitNode
                .getMember("projections")
                .map(SqlDeriveSelectProjectionsValue::fromNode)
                .orElseGet(SqlDeriveSelectProjectionsValue::allColumns);
    }

    public static SqlDeriveSelectProjectionsValue fromNode(Node node) {
        if (node.isStringNode()) {
            String value = node.expectStringNode().getValue();
            if (STAR.equals(value)) {
                return allColumns();
            }
            throw new IllegalArgumentException(
                    "projections string must be \"*\"; got '" + value + "'");
        }
        if (node.isArrayNode()) {
            ArrayNode arrayNode = node.expectArrayNode();
            return explicit(
                    arrayNode.getElements().stream()
                            .map(SqlDeriveSelectProjectionValue::fromNode)
                            .collect(Collectors.toList()));
        }
        throw new IllegalArgumentException(
                "projections must be \"*\" or a projection list; got " + node.getType());
    }

    public Node toNode() {
        if (allColumns) {
            return Node.from(STAR);
        }
        ArrayNode.Builder builder = ArrayNode.builder();
        for (SqlDeriveSelectProjectionValue projection : explicit) {
            builder.withValue(projection.toNode());
        }
        return builder.build();
    }
}
