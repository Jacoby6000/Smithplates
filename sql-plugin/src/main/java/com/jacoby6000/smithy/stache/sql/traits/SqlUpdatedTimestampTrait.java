package com.jacoby6000.smithy.stache.sql.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;

public final class SqlUpdatedTimestampTrait extends AnnotationTrait {
    public static final ShapeId ID = ShapeId.from("stache.codegen.sql#sqlUpdatedTimestamp");

    public SqlUpdatedTimestampTrait(ObjectNode node) {
        super(ID, node);
    }

    public SqlUpdatedTimestampTrait() {
        super(ID, Node.objectNode());
    }

    public static final class Provider extends AnnotationTrait.Provider<SqlUpdatedTimestampTrait> {
        public Provider() {
            super(ID, SqlUpdatedTimestampTrait::new);
        }
    }
}
