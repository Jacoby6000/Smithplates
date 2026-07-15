package com.jacoby6000.smithplates.http.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;

/**
 * Marks an operation as a bidirectional WebSocket endpoint. The operation's input shape is the union
 * (or structure) of messages the server can receive from the client; the operation's output shape is
 * the union (or structure) of messages the client can receive from the server. The operation must
 * also declare an {@code @http} binding whose {@code uri} is the WebSocket route path.
 */
public final class WebsocketTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.http#websocket");

    private WebsocketTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().build();
    }

    public static final class Builder extends AbstractTraitBuilder<WebsocketTrait, Builder> {
        @Override
        public WebsocketTrait build() {
            return new WebsocketTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            ObjectNode objectNode = value.expectObjectNode();
            return builder().sourceLocation(objectNode).build();
        }
    }
}
