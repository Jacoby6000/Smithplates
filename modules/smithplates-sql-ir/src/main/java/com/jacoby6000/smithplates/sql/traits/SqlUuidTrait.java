package com.jacoby6000.smithplates.sql.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;

public final class SqlUuidTrait extends AnnotationTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.sql#sqlUuid");

    public SqlUuidTrait(ObjectNode node) {
        super(ID, node);
    }

    public SqlUuidTrait() {
        super(ID, Node.objectNode());
    }

    public static final class Provider extends AnnotationTrait.Provider<SqlUuidTrait> {
        public Provider() {
            super(ID, SqlUuidTrait::new);
        }
    }
}
