package com.jacoby6000.smithplates.http.traits;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class HttpProblemTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithplates.codegen.http#httpProblem");
    public static final String DEFAULT_TYPE = "about:blank";
    private static final String TYPE = "type";
    private static final String TITLE = "title";
    private static final String DETAIL = "detail";
    private static final String CODE = "code";

    private final String type;
    private final String title;
    private final String detail;
    private final Integer code;

    private HttpProblemTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.type = builder.type != null ? builder.type : DEFAULT_TYPE;
        this.title = builder.title;
        this.detail = builder.detail;
        this.code = builder.code;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public Integer getCode() {
        return code;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        ObjectNode.Builder builder = Node.objectNodeBuilder().withMember(TITLE, title);
        if (!DEFAULT_TYPE.equals(type)) {
            builder.withMember(TYPE, type);
        }
        if (detail != null) {
            builder.withMember(DETAIL, detail);
        }
        if (code != null) {
            builder.withMember(CODE, code);
        }
        return builder.build();
    }

    public static final class Builder extends AbstractTraitBuilder<HttpProblemTrait, Builder> {
        private String type = DEFAULT_TYPE;
        private String title;
        private String detail;
        private Integer code;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder code(Integer code) {
            this.code = code;
            return this;
        }

        @Override
        public HttpProblemTrait build() {
            if (title == null || title.isEmpty()) {
                throw new IllegalStateException("@httpProblem requires a non-empty title");
            }
            return new HttpProblemTrait(this);
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
                    .getMember(TYPE)
                    .ifPresent(member -> builder.type(member.expectStringNode().getValue()));
            builder.title(objectNode.expectMember(TITLE).expectStringNode().getValue());
            objectNode
                    .getMember(DETAIL)
                    .ifPresent(member -> builder.detail(member.expectStringNode().getValue()));
            objectNode
                    .getMember(CODE)
                    .ifPresent(member -> builder.code(member.expectNumberNode().getValue().intValue()));
            return builder.build();
        }
    }
}
