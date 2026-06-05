package com.jacoby6000.smithy.stache.sql.traits;

import java.util.Objects;
import java.util.Optional;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlForeignKeyTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("stache.codegen.sql#sqlForeignKey");
    private static final String REFERENCES = "references";
    private static final String COLUMN = "column";

    private final String references;
    private final String column;

    private SqlForeignKeyTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.references = Objects.requireNonNull(builder.references, REFERENCES);
        this.column = builder.column;
    }

    public String getReferences() {
        return references;
    }

    public Optional<String> getColumn() {
        return Optional.ofNullable(column);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        ObjectNode.Builder nodeBuilder =
                Node.objectNodeBuilder().withMember(REFERENCES, references);
        if (column != null) {
            nodeBuilder.withMember(COLUMN, column);
        }
        return nodeBuilder.build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlForeignKeyTrait, Builder> {
        private String references;
        private String column;

        public Builder references(String references) {
            this.references = references;
            return this;
        }

        public Builder column(String column) {
            this.column = column;
            return this;
        }

        @Override
        public SqlForeignKeyTrait build() {
            return new SqlForeignKeyTrait(this);
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
                            .references(objectNode.expectStringMember(REFERENCES).getValue());
            objectNode
                    .getStringMember(COLUMN)
                    .ifPresent(stringNode -> builder.column(stringNode.getValue()));
            return builder.build();
        }
    }
}
