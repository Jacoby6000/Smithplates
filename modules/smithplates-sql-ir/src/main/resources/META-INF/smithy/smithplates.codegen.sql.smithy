$version: "2.0"

namespace smithplates.codegen.sql

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
Maps a table member to a database-generated UUID column. Implies @sqlUuid. Omit from
insert inputs. Primary keys with this trait must still appear on @sqlUpdate structures
to identify the row in the WHERE clause.
""")
@trait(selector: "member")
structure sqlAutoUuid {}

@documentation("""
Maps an Integer table member to a database-generated auto-increment column. The column
becomes `INTEGER PRIMARY KEY AUTOINCREMENT` (SQLite) or `GENERATED ALWAYS AS IDENTITY`
(Postgres). Omit from insert inputs. Primary keys with this trait must still appear on
@sqlUpdate structures to identify the row in the WHERE clause.
""")
@trait(selector: "member")
structure sqlAutoIncrement {}

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
