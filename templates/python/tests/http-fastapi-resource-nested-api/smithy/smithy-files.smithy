$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#http
use smithy.api#tags
use smithy.api#readonly

@httpService
service ProjectApi {
    version: "1"
    resources: [Project]
}

resource Project {
    identifiers: { projectId: String }
    properties: { name: String }
    create: CreateProject
    read: GetProject
    list: ListProjects
    resources: [Task]
}

resource Task {
    identifiers: { projectId: String, taskId: String }
    properties: { title: String }
    create: CreateProjectTask
    read: GetProjectTask
    list: ListProjectTasks
}

@tags(["projects"])
@http(method: "POST", uri: "/projects", code: 201)
operation CreateProject {
    input: CreateProjectInput
    output: ProjectOutput
}

@tags(["projects"])
@http(method: "GET", uri: "/projects/{projectId}", code: 200)
@readonly
operation GetProject {
    input: GetProjectInput
    output: ProjectOutput
}

@tags(["projects"])
@http(method: "GET", uri: "/projects", code: 200)
@readonly
operation ListProjects {
    input: ListProjectsInput
    output: ProjectListOutput
}

@tags(["project_tasks"])
@http(method: "POST", uri: "/projects/{projectId}/tasks", code: 201)
operation CreateProjectTask {
    input: CreateProjectTaskInput
    output: TaskOutput
}

@tags(["project_tasks"])
@http(method: "GET", uri: "/projects/{projectId}/tasks/{taskId}", code: 200)
@readonly
operation GetProjectTask {
    input: GetProjectTaskInput
    output: TaskOutput
}

@tags(["project_tasks"])
@http(method: "GET", uri: "/projects/{projectId}/tasks", code: 200)
@readonly
operation ListProjectTasks {
    input: ListProjectTasksInput
    output: TaskListOutput
}

structure CreateProjectInput for Project {
    @required
    $name
}

structure GetProjectInput for Project {
    @required
    @httpLabel
    $projectId
}

structure ListProjectsInput {
    @httpQuery("limit")
    limit: Integer
}

structure CreateProjectTaskInput for Task {
    @required
    @httpLabel
    $projectId

    @required
    $title
}

structure GetProjectTaskInput for Task {
    @required
    @httpLabel
    $projectId

    @required
    @httpLabel
    $taskId
}

structure ListProjectTasksInput for Task {
    @required
    @httpLabel
    $projectId

    @httpQuery("page")
    page: String
}

structure ProjectOutput {
    @required
    projectId: String

    @required
    name: String
}

structure ProjectListOutput {
    @required
    items: String
}

structure TaskOutput {
    @required
    projectId: String

    @required
    taskId: String

    @required
    title: String
}

structure TaskListOutput {
    @required
    items: String
}
