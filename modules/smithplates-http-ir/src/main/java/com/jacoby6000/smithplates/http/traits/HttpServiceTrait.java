package com.jacoby6000.smithplates.http.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class HttpServiceTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.http#httpService");
    public static final String DEFAULT_SERIALIZATION = "json";
    private static final String SERIALIZATION = "serialization";

    private final String serialization;

    private HttpServiceTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.serialization =
                builder.serialization != null ? builder.serialization : DEFAULT_SERIALIZATION;
    }

    public String getSerialization() {
        return serialization;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().withMember(SERIALIZATION, serialization).build();
    }

    public static final class Builder extends AbstractTraitBuilder<HttpServiceTrait, Builder> {
        private String serialization = DEFAULT_SERIALIZATION;

        public Builder serialization(String serialization) {
            this.serialization = serialization;
            return this;
        }

        @Override
        public HttpServiceTrait build() {
            return new HttpServiceTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            ObjectNode objectNode = value.expectObjectNode();
            Builder builder = builder().sourceLocation(objectNode);
            objectNode
                    .getMember(SERIALIZATION)
                    .ifPresent(
                            member -> builder.serialization(member.expectStringNode().getValue()));
            return builder.build();
        }
    }
}
