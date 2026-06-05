$version: "2.0"

namespace jacoby6000.codegen.sql

use smithy.api#Integer
use smithy.api#String
use smithy.api#documentation
use smithy.api#trait

@documentation("Maps a structure to a SQL table with the given physical name.")
@trait(selector: "structure")
structure sqlTable {
    @required
    name: String
}

@documentation("Overrides the SQL column name; defaults to the member name.")
@trait(selector: "member")
structure sqlColumn {
    name: String
}

@documentation("""
Maps a string shape or string member to VARCHAR with the given maximum length. Apply on
the member or on a string type alias (for example `@sqlVarchar(maxLength: 16) string Code`).
String shapes without this trait map to TEXT. On SQLite, VARCHAR length is enforced with
a CHECK constraint.
""")
@trait(selector: ":test(string, member > string)")
structure sqlVarchar {
    @required
    maxLength: Integer
}

@documentation("""
Maps a string shape or string member to a SQL UUID column. Apply on the member or on a
string type alias (for example `@sqlUuid string Uuid`). String shapes without this trait
map to TEXT (or VARCHAR when annotated with @sqlVarchar). UUID is never inferred from
shape names.
""")
@trait(selector: ":test(string, member > string)")
structure sqlUuid {}

@documentation("""
Stores the member in a JSON column. Use on list, map, structure, or union members (including
nested structures that are not @sqlTable). Document members map to JSON without this trait. The
trait is not applied implicitly.
""")
@trait(selector: "member")
structure sqlJson {}

@documentation("Marks a table member as part of the primary key.")
@trait(selector: "member")
structure sqlPrimaryKey {}

@documentation("Creates a SQL index on the annotated column.")
@trait(selector: "member")
structure sqlIndex {
    name: String
}

@documentation("""
Creates a unique SQL index on the annotated column. When applied to an @sqlForeignKey member,
the relationship from the containing table to the referenced table is modeled as one-to-one
instead of many-to-one.
""")
@trait(selector: "member")
structure sqlUniqueIndex {
    name: String
}

@documentation("""
References another @sqlTable structure by shape ID. When `column` is omitted,
the referenced table's @sqlPrimaryKey column is used. The relationship is many-to-one from
the containing table to the referenced table; use @sqlUniqueIndex on the foreign key member
for a one-to-one relationship.
""")
@trait(selector: "member")
structure sqlForeignKey {
    @required
    references: String

    column: String
}

@documentation("""
Sentinel input shape for @sqlDeriveInsert, @sqlDeriveUpdate, @sqlDeriveDelete, and @sqlDeriveSelectOne operations. Do not add
members; fields are derived from the target @sqlTable at code-generation time. Use as the
operation input: `input: DerivedStruct` (with `use jacoby6000.codegen.sql#DerivedStruct`).
For @sqlDeriveUpdate, codegen expands a structure with `whereClause` (primary key members)
and `updateFields` (non-primary-key, non-database-managed columns). For @sqlDeriveDelete and
@sqlDeriveSelectOne, codegen expands a structure with `whereClause` (primary key members only).
Sentinel output shape for @sqlDeriveSelect operations. Do not add members; fields are derived
from the operation `@sqlDeriveSelect` projections at code-generation time.
""")
structure DerivedStruct {}

@documentation("""
Derives an INSERT statement from an operation and the referenced @sqlTable shape. The
operation input must be `jacoby6000.codegen.sql#DerivedStruct`. Insert columns are every
non-auto-generated table member (required and optional). Database-managed members
(@sqlAutoUuid, @sqlCreatedTimestamp, @sqlUpdatedTimestamp) are omitted. For output, set the
operation output to the primary key member target type to RETURNING that column when the table
has a single primary key. Otherwise set output to a structure whose members name table columns
to RETURNING. `Unit` output is invalid.
""")
@trait(selector: "operation")
structure sqlDeriveInsert {
    @required
    targetTable: String
}

@documentation("""
Derives an UPDATE statement from an operation and the referenced @sqlTable shape. The
operation input must be `jacoby6000.codegen.sql#DerivedStruct`. Codegen expands input to
`whereClause` (all @sqlPrimaryKey members) and `updateFields` (non-primary-key members that
are not database-managed on update). The operation output must be `Boolean` (false when no
row matched or was updated). Generated SQL targets a single row via the primary key WHERE
clause and sets @sqlUpdatedTimestamp columns automatically.
""")
@trait(selector: "operation")
structure sqlDeriveUpdate {
    @required
    targetTable: String
}

@documentation("""
Derives a DELETE statement from an operation and the referenced @sqlTable shape. The
operation input must be `jacoby6000.codegen.sql#DerivedStruct`. Codegen expands input to
`whereClause` (all @sqlPrimaryKey members). The operation output must be `Boolean` (false
when no row was deleted). Generated SQL deletes a single row via the primary key WHERE clause
and uses RETURNING on primary key columns so callers can detect whether a row was removed.
""")
@trait(selector: "operation")
structure sqlDeriveDelete {
    @required
    targetTable: String
}

@documentation("""
Derives a SELECT-by-primary-key statement from an operation and the referenced @sqlTable shape.
The operation input must be `jacoby6000.codegen.sql#DerivedStruct`. Codegen expands input to
`whereClause` (all @sqlPrimaryKey members). The operation output must be the target @sqlTable
structure shape. Generated SQL selects every table column and filters by primary key.
""")
@trait(selector: "operation")
structure sqlDeriveSelectOne {
    @required
    targetTable: String
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
`jacoby6000.codegen.sql#DerivedStruct`; codegen expands a result structure from `projections`.
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

@documentation("""
Maps a table member to a database-generated UUID column. Implies @sqlUuid. Omit from
insert inputs. Primary keys with this trait must still appear on @sqlUpdate structures
to identify the row in the WHERE clause.
""")
@trait(selector: "member")
structure sqlAutoUuid {}

@documentation("Column value is set by the database on insert; omit from insert/update inputs. Defaults to the second-to-last table column unless @sqlColumnIndex overrides.")
@trait(selector: "member")
structure sqlCreatedTimestamp {}

@documentation("""
Column value is set by the database on insert and update; omit from insert/update
inputs. Generated UPDATE statements set this column automatically. Defaults to the last
table column unless @sqlColumnIndex overrides.
""")
@trait(selector: "member")
structure sqlUpdatedTimestamp {}

@documentation("""
Assigns a stable ordering weight for this @sqlTable member relative to other members on
the same structure. Members without @sqlColumnIndex keep Smithy definition order among
themselves and sort before @sqlCreatedTimestamp / @sqlUpdatedTimestamp columns unless those
traits are overridden here. Affects DDL column order, derived query column lists, and
generated row models.
""")
@trait(selector: "member")
structure sqlColumnIndex {
    @required
    index: Integer
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
