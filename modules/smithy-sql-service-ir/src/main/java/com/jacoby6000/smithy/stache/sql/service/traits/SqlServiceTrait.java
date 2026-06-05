package com.jacoby6000.smithy.stache.sql.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;

public final class SqlServiceTrait extends AnnotationTrait {
    public static final ShapeId ID = ShapeId.from("stache.codegen.sql#sqlService");

    public SqlServiceTrait(ObjectNode node) {
        super(ID, node);
    }

    public SqlServiceTrait() {
        super(ID, Node.objectNode());
    }

    public static final class Provider extends AnnotationTrait.Provider<SqlServiceTrait> {
        public Provider() {
            super(ID, SqlServiceTrait::new);
        }
    }
}
