package com.jacoby6000.smithy.sql.traits;

import java.util.Locale;
import java.util.Objects;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

public final class SqlDeriveSelectConditionValue {
    private static final String LEFT = "left";
    private static final String OPERATOR = "operator";
    private static final String RIGHT = "right";

    private final String left;
    private final String operator;
    private final String right;

    public SqlDeriveSelectConditionValue(String left, String operator, String right) {
        this.left = Objects.requireNonNull(left, LEFT);
        this.operator = Objects.requireNonNull(operator, OPERATOR);
        this.right = Objects.requireNonNull(right, RIGHT);
    }

    public String getLeft() {
        return left;
    }

    public String getOperator() {
        return operator;
    }

    public String getRight() {
        return right;
    }

    public static SqlDeriveSelectConditionValue fromNode(Node node) {
        ObjectNode objectNode = node.expectObjectNode();
        String left = objectNode.expectStringMember(LEFT).getValue();
        String operator =
                objectNode
                        .getStringMember(OPERATOR)
                        .map(stringNode -> stringNode.getValue().toLowerCase(Locale.ROOT))
                        .orElse("=");
        String right = objectNode.expectStringMember(RIGHT).getValue();
        return new SqlDeriveSelectConditionValue(left, operator, right);
    }

    public Node toNode() {
        return Node.objectNodeBuilder()
                .withMember(LEFT, left)
                .withMember(OPERATOR, operator)
                .withMember(RIGHT, right)
                .build();
    }
}
