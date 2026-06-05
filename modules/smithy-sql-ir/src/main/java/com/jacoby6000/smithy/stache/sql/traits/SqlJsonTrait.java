package com.jacoby6000.smithy.stache.sql.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;

public final class SqlJsonTrait extends AnnotationTrait {
    public static final ShapeId ID = ShapeId.from("stache.codegen.sql#sqlJson");

    public SqlJsonTrait(ObjectNode node) {
        super(ID, node);
    }

    public SqlJsonTrait() {
        super(ID, Node.objectNode());
    }

    public static final class Provider extends AnnotationTrait.Provider<SqlJsonTrait> {
        public Provider() {
            super(ID, SqlJsonTrait::new);
        }
    }
}
