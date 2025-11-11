package us.awfl.dsl

// Generalized placeholders resolved by yaml.Constants at generation time
val WORKFLOW_ID: Value[String] = str("WORKFLOW_ID")
val WORKFLOW_EXECUTION_ID: Value[String] = str("WORKFLOW_EXECUTION_ID")
val PROJECT_ID: Value[String] = str("PROJECT_ID")
val LOCATION: Value[String] = str("LOCATION")