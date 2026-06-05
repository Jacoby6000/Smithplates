package com.jacoby6000.smithy.stache.sql.traits;

import java.util.Locale;
import java.util.Objects;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

public final class SqlDeriveSelectOrderByValue {
    private static final String PROJECTION = "projection";
    private static final String DIRECTION = "direction";

    private final String projection;
    private final String direction;

    public SqlDeriveSelectOrderByValue(String projection, String direction) {
        this.projection = Objects.requireNonNull(projection, PROJECTION);
        this.direction = Objects.requireNonNull(direction, DIRECTION);
    }

    public String getProjection() {
        return projection;
    }

    public String getDirection() {
        return direction;
    }

    public static SqlDeriveSelectOrderByValue fromNode(Node node) {
        ObjectNode objectNode = node.expectObjectNode();
        String projection = objectNode.expectStringMember(PROJECTION).getValue();
        String direction =
                objectNode
                        .getStringMember(DIRECTION)
                        .map(n -> n.getValue().toLowerCase(Locale.ROOT))
                        .orElse("asc");
        return new SqlDeriveSelectOrderByValue(projection, direction);
    }

    public Node toNode() {
        return Node.objectNodeBuilder()
                .withMember(PROJECTION, projection)
                .withMember(DIRECTION, direction)
                .build();
    }
}
