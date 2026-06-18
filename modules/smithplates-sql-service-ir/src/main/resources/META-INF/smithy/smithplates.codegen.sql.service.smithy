$version: "2.0"

namespace smithplates.codegen.sql

use smithy.api#Integer
use smithy.api#String
use smithy.api#documentation
use smithy.api#trait

@documentation("""
Sentinel input shape for @sqlDeriveInsert, @sqlDeriveUpdate, @sqlDeriveDelete, and @sqlDeriveSelectOne operations. Do not add
members; fields are derived from the target @sqlTable at code-generation time. Use as the
operation input: `input: DerivedStruct` (with `use smithplates.codegen.sql#DerivedStruct`).
For @sqlDeriveUpdate, codegen expands a structure with `whereClause` (primary key members)
and `updateFields` (non-primary-key, non-database-managed columns). For @sqlDeriveDelete and
@sqlDeriveSelectOne, codegen expands a structure with `whereClause` (primary key members only).
Sentinel output shape for @sqlDeriveSelect operations. Do not add members; fields are derived
from the operation `@sqlDeriveSelect` projections at code-generation time.
""")
structure DerivedStruct {}

@documentation("""
Derives an INSERT statement from an operation and the referenced @sqlTable shape. The
operation input must be `smithplates.codegen.sql#DerivedStruct`. Insert columns are every
non-auto-generated table member (required and optional). Database-managed members
(@sqlAutoUuid, @sqlCreatedTimestamp, @sqlUpdatedTimestamp) are omitted. For output, set the
operation output to a structure whose members name table columns to RETURNING. For example,
`structure CreateFooOutput { @required id: String }` returns the `id` column. `Unit` output is
invalid.
""")
@trait(selector: "operation")
structure sqlDeriveInsert {
    @required
    targetTable: String
}

@documentation("""
Derives an UPDATE statement from an operation and the referenced @sqlTable shape. The
operation input must be `smithplates.codegen.sql#DerivedStruct`. Codegen expands input to
`whereClause` (all @sqlPrimaryKey members) and `updateFields` (non-primary-key members that
are not database-managed on update). The operation output should be a structure with exactly
one Boolean member (false when no row matched or was updated), such as
`structure UpdateFooOutput { @required updated: Boolean }`. Generated SQL targets a single row
via the primary key WHERE clause and sets @sqlUpdatedTimestamp columns automatically.
""")
@trait(selector: "operation")
structure sqlDeriveUpdate {
    @required
    targetTable: String
}

@documentation("""
Derives a DELETE statement from an operation and the referenced @sqlTable shape. The
operation input must be `smithplates.codegen.sql#DerivedStruct`. Codegen expands input to
`whereClause` (all @sqlPrimaryKey members). The operation output should be a structure with
exactly one Boolean member (false when no row was deleted), such as
`structure DeleteFooOutput { @required deleted: Boolean }`. Generated SQL deletes a single row
via the primary key WHERE clause and uses RETURNING on primary key columns so callers can detect
whether a row was removed.
""")
@trait(selector: "operation")
structure sqlDeriveDelete {
    @required
    targetTable: String
}

@documentation("""
Derives a SELECT-by-primary-key statement from an operation and the referenced @sqlTable shape.
The operation input must be `smithplates.codegen.sql#DerivedStruct`. Codegen expands input to
`whereClause` (all @sqlPrimaryKey members). When `joins` is empty, the operation output must
be the target @sqlTable structure shape. When `joins` lists related @sqlTable shapes, the
operation output must be `smithplates.codegen.sql#DerivedStruct`; codegen expands a result
structure with the target table members plus nested joined structures. Join ON clauses are
derived from @sqlForeignKey relationships. A joined table whose foreign key points at the
target table is modeled as a list member (one-to-many). A target-table foreign key pointing
at a joined table is modeled as a singular nested member (many-to-one or one-to-one); required
when the foreign key member is required.
Generated SQL selects every column from the target table and joined tables and filters by
primary key. Each join after the first resolves its ON clause from the nearest prior joined
table when no direct @sqlForeignKey exists on the target table.
""")
@trait(selector: "operation")
structure sqlDeriveSelectOne {
    @required
    targetTable: String

    joins: sqlSelectJoinList = []
}

structure sqlDeriveSelectFrom {
    @required
    table: String

    alias: String
}

structure sqlDeriveSelectProjection {
    @required
    alias: String

    /// Table column reference (`alias.column`) or bare column name when unique across `from` and joins.
    @required
    source: String

    aggregate: SqlAggregateFunction
}

list sqlDeriveSelectProjectionList {
    member: sqlDeriveSelectProjection
}

enum SqlComparisonOperator {
    EQ = "="
}

structure sqlDeriveSelectCondition {
    @required
    left: String

    operator: SqlComparisonOperator = "="

    @required
    right: String
}

list sqlDeriveSelectConditionList {
    member: sqlDeriveSelectCondition
}

list sqlDeriveSelectGroupByList {
    member: String
}

enum SqlSortDirection {
    ASC = "asc"
    DESC = "desc"
}

structure sqlDeriveSelectOrderBy {
    @required
    projection: String

    direction: SqlSortDirection = "asc"
}

list sqlDeriveSelectOrderByList {
    member: sqlDeriveSelectOrderBy
}

@documentation("""
Derives a SELECT statement from an operation and the referenced @sqlTable shape. The operation
input must be a structure whose members supply bind parameters. The operation output must be
`smithplates.codegen.sql#DerivedStruct`; codegen expands a result structure from `projections`.
Use `projections: "*"` (the default) to select all columns from `from` and joined tables as
explicit `{alias}_{member}` result fields; provide an explicit projection list for aggregates,
`groupBy`, `having`, or `orderBy`. Use `from: { table: ShapeId, alias: String? }` for the
primary table and optional query alias.
Join entries accept `tableAlias`. Reference input bind parameters as `input.memberName`.
Reference table columns as `alias.columnName` (or bare column/member names when unique).
`where` conditions may reference table columns on `from`/joins only; `having` may reference
projections (including aggregates) or table columns. Join ON clauses are derived from
@sqlForeignKey relationships between the primary table and each joined table.
""")
@trait(selector: "operation")
structure sqlDeriveSelect {
    @required
    from: sqlDeriveSelectFrom

/// `"*"` selects all columns from `from` and joined tables; otherwise an explicit projection list.
    projections: Document = "*"

    joins: sqlSelectJoinList = []

    where: sqlDeriveSelectConditionList = []

    groupBy: sqlDeriveSelectGroupByList = []

    having: sqlDeriveSelectConditionList = []

    orderBy: sqlDeriveSelectOrderByList = []

    offsetInputMember: String

    limitInputMember: String
}

@documentation("""
Maps a structure to an UPDATE statement for the referenced @sqlTable shape. All
@sqlPrimaryKey members must appear. Member names must match table members.
@sqlCreatedTimestamp and @sqlUpdatedTimestamp table columns must not appear on the update
structure (they are database-managed). @sqlAutoUuid primary keys must appear to identify
the row.
""")
@trait(selector: "structure")
structure sqlUpdate {
    @required
    tableRef: String
}

enum SqlJoinType {
    INNER = "inner"
    LEFT = "left"
    RIGHT = "right"
    FULL = "full"
    CROSS = "cross"
}

structure sqlSelectJoin {
    @required
    table: String

    type: SqlJoinType = "inner"

    tableAlias: String
}

list sqlSelectJoinList {
    member: sqlSelectJoin
}

enum SqlAggregateFunction {
    SUM = "sum"
    COUNT = "count"
    MAX = "max"
    MIN = "min"
    AVG = "avg"
}

@documentation("""
Marks a Smithy service as a SQL data-access service. Its operations become methods on a
single generated repository class. Declare operations directly on the service; do not use
`resources`. The natural 1:1 mapping is table to resource, but resource `properties` cannot
carry SQL member traits (`@sqlPrimaryKey`, `@sqlForeignKey`, `@sqlColumn`, and so on) that
belong on `@sqlTable` structure members. Workarounds would split annotations away from the
fields they describe and worsen authoring UX without meaningful benefit.

Operations do not automatically generate or bind SQL; query linkage is defined separately
in later work.
""")
@trait(selector: "service")
structure sqlService {}
