package com.jacoby6000.smithy.stache.sql.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;

public final class SqlAutoUuidTrait extends AnnotationTrait {
    public static final ShapeId ID = ShapeId.from("stache.codegen.sql#sqlAutoUuid");

    public SqlAutoUuidTrait(ObjectNode node) {
        super(ID, node);
    }

    public SqlAutoUuidTrait() {
        super(ID, Node.objectNode());
    }

    public static final class Provider extends AnnotationTrait.Provider<SqlAutoUuidTrait> {
        public Provider() {
            super(ID, SqlAutoUuidTrait::new);
        }
    }
}
