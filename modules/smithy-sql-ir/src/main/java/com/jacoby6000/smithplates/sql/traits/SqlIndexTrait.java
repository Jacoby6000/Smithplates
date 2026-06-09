package com.jacoby6000.smithplates.sql.traits;

import java.util.Optional;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlIndexTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.sql#sqlIndex");
    private static final String NAME = "name";

    private final String name;

    private SqlIndexTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.name = builder.name;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        ObjectNode.Builder nodeBuilder = Node.objectNodeBuilder();
        if (name != null) {
            nodeBuilder.withMember(NAME, name);
        }
        return nodeBuilder.build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlIndexTrait, Builder> {
        private String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public SqlIndexTrait build() {
            return new SqlIndexTrait(this);
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
            objectNode
                    .getStringMember(NAME)
                    .ifPresent(stringNode -> builder.name(stringNode.getValue()));
            return builder.build();
        }
    }
}
