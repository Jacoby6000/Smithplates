package com.jacoby6000.smithplates.http.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;

import java.util.regex.Pattern;

public final class HttpCookieAuthTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.http#httpCookieAuth");
    private static final String NAME = "name";
    private static final Pattern COOKIE_NAME_PATTERN = Pattern.compile("^[!#$%&'*+.^_`|~A-Za-z0-9-]+$");

    private final String name;

    private HttpCookieAuthTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.name = builder.name;
    }

    public String getName() {
        return name;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        return Node.objectNodeBuilder().withMember(NAME, name).build();
    }

    public static final class Builder extends AbstractTraitBuilder<HttpCookieAuthTrait, Builder> {
        private String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public HttpCookieAuthTrait build() {
            if (name == null || !COOKIE_NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalStateException("httpCookieAuth name must be a valid HTTP cookie name");
            }
            return new HttpCookieAuthTrait(this);
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
                    .name(objectNode.expectStringMember(NAME).getValue())
                    .build();
        }
    }
}
