package us.awfl.dsl

// Server-owned bindings; GOOGLE_CLOUD_* names are YAML backend compatibility only.
val WORKFLOW_ID: Value[String] = str(CelGlobal(GlobalId.WorkflowId))
val WORKFLOW_EXECUTION_ID: Value[String] = str(CelGlobal(GlobalId.ExecutionId))
val PROJECT_ID: Value[String] = str(CelGlobal(GlobalId.CloudProjectId))
val LOCATION: Value[String] = str(CelGlobal(GlobalId.Region))
