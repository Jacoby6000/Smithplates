package com.jacoby6000.smithplates.sql.traits;

import java.util.Objects;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlTableTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.sql#sqlTable");
    private static final String NAME = "name";

    private final String name;

    private SqlTableTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.name = Objects.requireNonNull(builder.name, "name");
    }

    public String getName() {
        return name;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().withMember(NAME, name).build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlTableTrait, Builder> {
        private String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public SqlTableTrait build() {
            return new SqlTableTrait(this);
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
                    .name(objectNode.expectStringMember(NAME).getValue())
                    .build();
        }
    }
}
