package com.jacoby6000.smithplates.sql.service.traits;

import java.util.Objects;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlUpdateTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.sql#sqlUpdate");
    private static final String TABLE_REF = "tableRef";

    private final String tableRef;

    private SqlUpdateTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.tableRef = Objects.requireNonNull(builder.tableRef, TABLE_REF);
    }

    public String getTableRef() {
        return tableRef;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().withMember(TABLE_REF, tableRef).build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlUpdateTrait, Builder> {
        private String tableRef;

        public Builder tableRef(String tableRef) {
            this.tableRef = tableRef;
            return this;
        }

        @Override
        public SqlUpdateTrait build() {
            return new SqlUpdateTrait(this);
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
                    .tableRef(objectNode.expectStringMember(TABLE_REF).getValue())
                    .build();
        }
    }
}
