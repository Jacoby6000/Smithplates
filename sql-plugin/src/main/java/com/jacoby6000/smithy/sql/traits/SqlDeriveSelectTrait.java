package com.jacoby6000.smithy.sql.traits;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class SqlDeriveSelectTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("jacoby6000.codegen.sql#sqlDeriveSelect");
    private static final String FROM = "from";
    private static final String PROJECTIONS = "projections";
    private static final String JOINS = "joins";
    private static final String WHERE = "where";
    private static final String GROUP_BY = "groupBy";
    private static final String HAVING = "having";
    private static final String ORDER_BY = "orderBy";
    private static final String OFFSET_INPUT_MEMBER = "offsetInputMember";
    private static final String LIMIT_INPUT_MEMBER = "limitInputMember";

    private final SqlDeriveSelectFromValue from;
    private final SqlDeriveSelectProjectionsValue projections;
    private final List<SqlSelectJoinValue> joins;
    private final List<SqlDeriveSelectConditionValue> where;
    private final List<String> groupBy;
    private final List<SqlDeriveSelectConditionValue> having;
    private final List<SqlDeriveSelectOrderByValue> orderBy;
    private final String offsetInputMember;
    private final String limitInputMember;

    private SqlDeriveSelectTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.from = Objects.requireNonNull(builder.from, FROM);
        this.projections = Objects.requireNonNull(builder.projections, PROJECTIONS);
        this.joins = Collections.unmodifiableList(builder.joins);
        this.where = Collections.unmodifiableList(builder.where);
        this.groupBy = Collections.unmodifiableList(builder.groupBy);
        this.having = Collections.unmodifiableList(builder.having);
        this.orderBy = Collections.unmodifiableList(builder.orderBy);
        this.offsetInputMember = builder.offsetInputMember;
        this.limitInputMember = builder.limitInputMember;
    }

    public SqlDeriveSelectFromValue getFrom() {
        return from;
    }

    public SqlDeriveSelectProjectionsValue getProjections() {
        return projections;
    }

    public List<SqlSelectJoinValue> getJoins() {
        return joins;
    }

    public List<SqlDeriveSelectConditionValue> getWhere() {
        return where;
    }

    public List<String> getGroupBy() {
        return groupBy;
    }

    public List<SqlDeriveSelectConditionValue> getHaving() {
        return having;
    }

    public List<SqlDeriveSelectOrderByValue> getOrderBy() {
        return orderBy;
    }

    public Optional<String> getOffsetInputMember() {
        return Optional.ofNullable(offsetInputMember);
    }

    public Optional<String> getLimitInputMember() {
        return Optional.ofNullable(limitInputMember);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        ObjectNode.Builder nodeBuilder =
                Node.objectNodeBuilder()
                        .withMember(FROM, from.toNode())
                        .withMember(PROJECTIONS, projections.toNode());
        if (!joins.isEmpty()) {
            nodeBuilder.withMember(JOINS, toJoinArray(joins));
        }
        if (!where.isEmpty()) {
            nodeBuilder.withMember(WHERE, toConditionArray(where));
        }
        if (!groupBy.isEmpty()) {
            nodeBuilder.withMember(GROUP_BY, toStringArray(groupBy));
        }
        if (!having.isEmpty()) {
            nodeBuilder.withMember(HAVING, toConditionArray(having));
        }
        if (!orderBy.isEmpty()) {
            nodeBuilder.withMember(ORDER_BY, toOrderByArray(orderBy));
        }
        if (offsetInputMember != null) {
            nodeBuilder.withMember(OFFSET_INPUT_MEMBER, offsetInputMember);
        }
        if (limitInputMember != null) {
            nodeBuilder.withMember(LIMIT_INPUT_MEMBER, limitInputMember);
        }
        return nodeBuilder.build();
    }

    private static ArrayNode toJoinArray(List<SqlSelectJoinValue> values) {
        ArrayNode.Builder builder = ArrayNode.builder();
        for (SqlSelectJoinValue value : values) {
            builder.withValue(value.toNode());
        }
        return builder.build();
    }

    private static ArrayNode toConditionArray(List<SqlDeriveSelectConditionValue> values) {
        ArrayNode.Builder builder = ArrayNode.builder();
        for (SqlDeriveSelectConditionValue value : values) {
            builder.withValue(value.toNode());
        }
        return builder.build();
    }

    private static ArrayNode toStringArray(List<String> values) {
        ArrayNode.Builder builder = ArrayNode.builder();
        for (String value : values) {
            builder.withValue(value);
        }
        return builder.build();
    }

    private static ArrayNode toOrderByArray(List<SqlDeriveSelectOrderByValue> values) {
        ArrayNode.Builder builder = ArrayNode.builder();
        for (SqlDeriveSelectOrderByValue value : values) {
            builder.withValue(value.toNode());
        }
        return builder.build();
    }

    public static final class Builder extends AbstractTraitBuilder<SqlDeriveSelectTrait, Builder> {
        private SqlDeriveSelectFromValue from;
        private SqlDeriveSelectProjectionsValue projections = SqlDeriveSelectProjectionsValue.allColumns();
        private List<SqlSelectJoinValue> joins = List.of();
        private List<SqlDeriveSelectConditionValue> where = List.of();
        private List<String> groupBy = List.of();
        private List<SqlDeriveSelectConditionValue> having = List.of();
        private List<SqlDeriveSelectOrderByValue> orderBy = List.of();
        private String offsetInputMember;
        private String limitInputMember;

        public Builder from(SqlDeriveSelectFromValue from) {
            this.from = from;
            return this;
        }

        public Builder projections(SqlDeriveSelectProjectionsValue projections) {
            this.projections = projections;
            return this;
        }

        public Builder joins(List<SqlSelectJoinValue> joins) {
            this.joins = joins;
            return this;
        }

        public Builder where(List<SqlDeriveSelectConditionValue> where) {
            this.where = where;
            return this;
        }

        public Builder groupBy(List<String> groupBy) {
            this.groupBy = groupBy;
            return this;
        }

        public Builder having(List<SqlDeriveSelectConditionValue> having) {
            this.having = having;
            return this;
        }

        public Builder orderBy(List<SqlDeriveSelectOrderByValue> orderBy) {
            this.orderBy = orderBy;
            return this;
        }

        public Builder offsetInputMember(String offsetInputMember) {
            this.offsetInputMember = offsetInputMember;
            return this;
        }

        public Builder limitInputMember(String limitInputMember) {
            this.limitInputMember = limitInputMember;
            return this;
        }

        @Override
        public SqlDeriveSelectTrait build() {
            return new SqlDeriveSelectTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            ObjectNode objectNode = value.expectObjectNode();
            Builder builder =
                    builder()
                            .sourceLocation(objectNode)
                            .from(SqlDeriveSelectFromValue.fromNode(objectNode.expectObjectMember(FROM)))
                            .projections(SqlDeriveSelectProjectionsValue.fromTraitNode(objectNode));
            objectNode
                    .getArrayMember(JOINS)
                    .ifPresent(
                            arrayNode ->
                                    builder.joins(
                                            arrayNode.getElements().stream()
                                                    .map(SqlSelectJoinValue::fromNode)
                                                    .collect(Collectors.toList())));
            objectNode
                    .getArrayMember(WHERE)
                    .ifPresent(
                            arrayNode ->
                                    builder.where(
                                            arrayNode.getElements().stream()
                                                    .map(SqlDeriveSelectConditionValue::fromNode)
                                                    .collect(Collectors.toList())));
            objectNode
                    .getArrayMember(GROUP_BY)
                    .ifPresent(
                            arrayNode ->
                                    builder.groupBy(
                                            arrayNode.getElements().stream()
                                                    .map(Node::expectStringNode)
                                                    .map(n -> n.getValue())
                                                    .collect(Collectors.toList())));
            objectNode
                    .getArrayMember(HAVING)
                    .ifPresent(
                            arrayNode ->
                                    builder.having(
                                            arrayNode.getElements().stream()
                                                    .map(SqlDeriveSelectConditionValue::fromNode)
                                                    .collect(Collectors.toList())));
            objectNode
                    .getArrayMember(ORDER_BY)
                    .ifPresent(
                            arrayNode ->
                                    builder.orderBy(
                                            arrayNode.getElements().stream()
                                                    .map(SqlDeriveSelectOrderByValue::fromNode)
                                                    .collect(Collectors.toList())));
            objectNode
                    .getStringMember(OFFSET_INPUT_MEMBER)
                    .ifPresent(node -> builder.offsetInputMember(node.getValue()));
            objectNode
                    .getStringMember(LIMIT_INPUT_MEMBER)
                    .ifPresent(node -> builder.limitInputMember(node.getValue()));
            return builder.build();
        }
    }
}
