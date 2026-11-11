package us.awfl.dsl

import us.awfl.dsl.CelOps.*

/** Compile-time shape verification: ordinary CEL/Call constructors, no new AST. */
object OperationNamesSmoke {
  def main(args: Array[String]): Unit = {
    val expression: Cel = CelFunc(CelFunctions.Length, CelConst("[]"))
    assert(expression == CelFunc("value.length", CelConst("[]")))
    val arithmetic: Cel = CelConst("1") + CelConst("2")
    assert(arithmetic == CelOp(CelConst("1"), "+", CelConst("2")))
    val call = Call[Map[String, String], String]("http", CallOperations.HttpPost,
      obj(Map("url" -> "https://example.org")))
    assert(call.call == "http.post")
    assert(CelFunctions.JsonEncodeString != "json.encode_to_string")
    assert(CallOperations.WorkflowRun !=
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run")
    println("DSL operation constants preserve existing CelFunc, CelOp and Call")
  }
}
