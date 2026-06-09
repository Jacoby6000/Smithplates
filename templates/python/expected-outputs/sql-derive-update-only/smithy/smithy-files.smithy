$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlDeriveUpdate
use smithplates.codegen.sql#sqlService

@sqlTable(name: "bookmarks")
structure Bookmark {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    title: String
}

@sqlDeriveUpdate(targetTable: "example#Bookmark")
operation UpdateBookmark {
    input: DerivedStruct
    output: Boolean
}

@sqlService
service BookmarkRepository {
    version: "1"
    operations: [UpdateBookmark]
}
