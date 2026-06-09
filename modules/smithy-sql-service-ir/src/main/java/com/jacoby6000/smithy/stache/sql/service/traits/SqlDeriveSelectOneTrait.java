package com.jacoby6000.smithy.stache.sql.service.traits;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlDeriveSelectOneTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("stache.codegen.sql#sqlDeriveSelectOne");
    private static final String TARGET_TABLE = "targetTable";
    private static final String JOINS = "joins";

    private final String targetTable;
    private final List<SqlSelectJoinValue> joins;

    private SqlDeriveSelectOneTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.targetTable = Objects.requireNonNull(builder.targetTable, TARGET_TABLE);
        this.joins = Collections.unmodifiableList(builder.joins);
    }

    public String getTargetTable() {
        return targetTable;
    }

    public List<SqlSelectJoinValue> getJoins() {
        return joins;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        ObjectNode.Builder nodeBuilder =
                Node.objectNodeBuilder().withMember(TARGET_TABLE, targetTable);
        if (!joins.isEmpty()) {
            nodeBuilder.withMember(JOINS, toJoinArray(joins));
        }
        return nodeBuilder.build();
    }

    private static ArrayNode toJoinArray(List<SqlSelectJoinValue> values) {
        ArrayNode.Builder builder = ArrayNode.builder();
        for (SqlSelectJoinValue value : values) {
            builder.withValue(value.toNode());
        }
        return builder.build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlDeriveSelectOneTrait, Builder> {
        private String targetTable;
        private List<SqlSelectJoinValue> joins = List.of();

        public Builder targetTable(String targetTable) {
            this.targetTable = targetTable;
            return this;
        }

        public Builder joins(List<SqlSelectJoinValue> joins) {
            this.joins = joins;
            return this;
        }

        @Override
        public SqlDeriveSelectOneTrait build() {
            return new SqlDeriveSelectOneTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            ObjectNode objectNode = value.expectObjectNode();
            Builder builder =
                    builder()
                            .sourceLocation(objectNode)
                            .targetTable(objectNode.expectStringMember(TARGET_TABLE).getValue());
            objectNode
                    .getArrayMember(JOINS)
                    .ifPresent(
                            arrayNode ->
                                    builder.joins(
                                            arrayNode.getElements().stream()
                                                    .map(SqlSelectJoinValue::fromNode)
                                                    .collect(Collectors.toList())));
            return builder.build();
        }
    }
}
