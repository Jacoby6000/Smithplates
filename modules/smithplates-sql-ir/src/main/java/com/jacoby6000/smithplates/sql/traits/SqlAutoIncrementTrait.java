package com.jacoby6000.smithplates.sql.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;

public final class SqlAutoIncrementTrait extends AnnotationTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.sql#sqlAutoIncrement");

    public SqlAutoIncrementTrait(ObjectNode node) {
        super(ID, node);
    }

    public SqlAutoIncrementTrait() {
        super(ID, Node.objectNode());
    }

    public static final class Provider extends AnnotationTrait.Provider<SqlAutoIncrementTrait> {
        public Provider() {
            super(ID, SqlAutoIncrementTrait::new);
        }
    }
}
