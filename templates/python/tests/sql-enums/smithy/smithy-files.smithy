$version: "2.0"
namespace example

use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable

enum TaskStatus {
    OPEN = "open"
    CLOSED = "closed"
}

intEnum TaskPriority {
    LOW = 1
    HIGH = 2
}

@sqlTable(name: "tasks")
structure Task {
    @sqlPrimaryKey
    id: String
    label: String
    status: TaskStatus
    priority: TaskPriority
}

@sqlDeriveInsert(targetTable: "example#Task")
operation CreateTask {
    input: DerivedStruct
    output: String
}

@sqlDeriveSelectOne(targetTable: "example#Task")
operation GetTask {
    input: DerivedStruct
    output: Task
}

@sqlService
service TaskRepository {
    version: "1"
    operations: [CreateTask, GetTask]
}
