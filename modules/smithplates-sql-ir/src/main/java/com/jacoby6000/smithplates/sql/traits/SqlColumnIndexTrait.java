package com.jacoby6000.smithplates.sql.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlColumnIndexTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.sql#sqlColumnIndex");
    private static final String INDEX = "index";

    private final int index;

    private SqlColumnIndexTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.index = builder.index;
    }

    public int getIndex() {
        return index;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().withMember(INDEX, index).build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlColumnIndexTrait, Builder> {
        private int index;

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        @Override
        public SqlColumnIndexTrait build() {
            return new SqlColumnIndexTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            ObjectNode objectNode = value.expectObjectNode();
            Builder builder = builder().sourceLocation(objectNode);
            builder.index(objectNode.expectNumberMember(INDEX).getValue().intValue());
            return builder.build();
        }
    }
}
