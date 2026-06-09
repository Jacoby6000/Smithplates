package com.jacoby6000.smithplates.sql.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlVarcharTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.sql#sqlVarchar");
    private static final String MAX_LENGTH = "maxLength";

    private final int maxLength;

    private SqlVarcharTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.maxLength = builder.maxLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().withMember(MAX_LENGTH, maxLength).build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlVarcharTrait, Builder> {
        private int maxLength;

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        @Override
        public SqlVarcharTrait build() {
            return new SqlVarcharTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            ObjectNode objectNode = value.expectObjectNode();
            int maxLength = objectNode.expectNumberMember(MAX_LENGTH).getValue().intValue();
            return builder().sourceLocation(objectNode).maxLength(maxLength).build();
        }
    }
}
