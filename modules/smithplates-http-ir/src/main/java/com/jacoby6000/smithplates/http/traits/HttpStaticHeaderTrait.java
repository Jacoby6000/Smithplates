package com.jacoby6000.smithplates.http.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class HttpStaticHeaderTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.http#httpStaticHeader");
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final String name;
    private final String value;

    private HttpStaticHeaderTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.name = builder.name;
        this.value = builder.value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().withMember(NAME, name).withMember(VALUE, value).build();
    }

    public static final class Builder extends AbstractTraitBuilder<HttpStaticHeaderTrait, Builder> {
        private String name;
        private String value;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        public HttpStaticHeaderTrait build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("@httpStaticHeader requires a non-empty name");
            }
            if (value == null) {
                throw new IllegalStateException("@httpStaticHeader requires a value");
            }
            return new HttpStaticHeaderTrait(this);
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
            builder.name(objectNode.expectMember(NAME).expectStringNode().getValue());
            builder.value(objectNode.expectMember(VALUE).expectStringNode().getValue());
            return builder.build();
        }
    }
}
