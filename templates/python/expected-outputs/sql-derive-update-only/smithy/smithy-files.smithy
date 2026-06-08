$version: "2.0"
namespace example

use stache.codegen.sql#sqlAutoUuid
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlDeriveUpdate
use stache.codegen.sql#sqlService

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
