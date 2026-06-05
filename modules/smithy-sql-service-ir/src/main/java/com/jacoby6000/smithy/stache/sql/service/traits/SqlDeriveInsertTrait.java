package com.jacoby6000.smithy.stache.sql.service.traits;

import java.util.Objects;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlDeriveInsertTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("stache.codegen.sql#sqlDeriveInsert");
    private static final String TARGET_TABLE = "targetTable";

    private final String targetTable;

    private SqlDeriveInsertTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.targetTable = Objects.requireNonNull(builder.targetTable, TARGET_TABLE);
    }

    public String getTargetTable() {
        return targetTable;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().withMember(TARGET_TABLE, targetTable).build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlDeriveInsertTrait, Builder> {
        private String targetTable;

        public Builder targetTable(String targetTable) {
            this.targetTable = targetTable;
            return this;
        }

        @Override
        public SqlDeriveInsertTrait build() {
            return new SqlDeriveInsertTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            ObjectNode objectNode = value.expectObjectNode();
            return builder()
                    .sourceLocation(objectNode)
                    .targetTable(objectNode.expectStringMember(TARGET_TABLE).getValue())
                    .build();
        }
    }
}
