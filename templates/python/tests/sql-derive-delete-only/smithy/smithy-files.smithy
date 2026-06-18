$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlDeriveDelete
use smithplates.codegen.sql#sqlService
use smithy.api#required

@sqlTable(name: "bookmarks")
structure Bookmark {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    title: String
}

structure DeleteBookmarkOutput {
    @required
    deleted: Boolean
}

@sqlDeriveDelete(targetTable: "example#Bookmark")
operation DeleteBookmark {
    input: DerivedStruct
    output: DeleteBookmarkOutput
}

@sqlService
service BookmarkRepository {
    version: "1"
    operations: [DeleteBookmark]
}
