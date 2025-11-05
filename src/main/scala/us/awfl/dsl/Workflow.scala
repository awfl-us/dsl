package us.awfl.dsl

/**
  * Represents a YAML-exportable Google Workflows definition.
  *
  * @param steps Ordered list of steps composing the workflow.
  * @param name  Optional workflow name (needed for deployment).
  */
case class Workflow[T](steps: (List[Step[_, _]], BaseValue[T]), name: Option[String] = None)

case class ConnectorParams(skip_polling: Boolean)
case class RunWorkflowArgs[T](
    workflow_id:      BaseValue[String],
    argument:         BaseValue[T],
    location:         BaseValue[String] = LOCATION,
    project_id:       BaseValue[String] = PROJECT_ID,
    connector_params: ConnectorParams = ConnectorParams(false)
)
