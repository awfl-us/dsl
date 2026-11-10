package us.awfl.dsl

/** Run with: sbt 'Test / runMain us.awfl.dsl.OperationRegistrySmoke' */
object OperationRegistrySmoke {
  def main(args: Array[String]): Unit = {
    assert(FunctionId.values.length == OperationRegistry.functions.size)
    assert(CallId.values.length == OperationRegistry.calls.size)
    assert(FunctionId.fromLegacy("json.encode") == Some(FunctionId.JsonEncodeBytes))
    assert(FunctionId.fromWire("json.encode_string") == Some(FunctionId.JsonEncodeString))
    assert(CallId.fromLegacy("events.await_callback") == Some(CallId.CallbackAwait))
    assert(CallId.fromWire("workflow.run") == Some(CallId.WorkflowRun))
    assert(OperatorId.fromLegacy("and", 2) == Some(OperatorId.And))
    assert(OperatorId.And.shortCircuit)
    assert(OperatorId.Not.arity == 1)
    assert(OperationRegistry.functions(FunctionId.ListAppend).result == ValueKind.Sequence)
    assert(OperationRegistry.functions(FunctionId.JsonEncodeBytes).result == ValueKind.Bytes)
    assert(OperationRegistry.functions(FunctionId.UuidGenerate).deterministic == false)
    assert(OperationRegistry.resolveLegacyFunction("unknown.provider", "workflow.test").isLeft)
    assert(OperationRegistry.resolveLegacyCall("unknown.provider", "workflow.test").isLeft)
    assert(OperationRegistry.requireNative(FunctionId.MapGet, 1, "workflow.test").isLeft)
    assert(OperationRegistry.requireNative(CallId.HttpPost, 1, "workflow.test").isLeft)
    println("Operation registry smoke checks passed")
  }
}
