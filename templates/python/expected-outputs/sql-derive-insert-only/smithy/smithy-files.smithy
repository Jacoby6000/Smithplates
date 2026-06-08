$version: "2.0"
namespace example

use stache.codegen.sql#sqlAutoUuid
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlDeriveInsert
use stache.codegen.sql#sqlService

@sqlTable(name: "bookmarks")
structure Bookmark {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    title: String
}

@sqlDeriveInsert(targetTable: "example#Bookmark")
operation CreateBookmark {
    input: DerivedStruct
    output: String
}

@sqlService
service BookmarkRepository {
    version: "1"
    operations: [CreateBookmark]
}
