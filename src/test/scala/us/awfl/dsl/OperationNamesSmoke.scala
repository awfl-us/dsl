package us.awfl.dsl

import us.awfl.dsl.CelOps.*

/** Compile-time and runtime verification: keep the original CelFunc/Call AST. */
object OperationNamesSmoke {
  def main(args: Array[String]): Unit = {
    assert(time.now() == CelFunc("sys.now"))
    assert(time.now() == CelFunc(CelFunctions.Time.Now))
    assert(environment.get("GOOGLE_CLOUD_PROJECT_ID") ==
      CelFunc("sys.get_env", CelStr("GOOGLE_CLOUD_PROJECT_ID")))
    assert(map.get(CelConst("input"), "foo") ==
      CelFunc("map.get", CelConst("input"), CelStr("foo")))
    assert(map.merge(CelConst("left"), CelConst("right")).name == "map.merge")
    assert(list.concat(CelConst("items"), 2).name == "list.concat")
    assert(json.encode_to_string(CelConst("data")).name == "json.encode_to_string")
    assert(json.encode(CelConst("data")).name == "json.encode")
    assert(base64.encode(CelConst("bytes"), true).name == "base64.encode")
    assert(text.to_lower("HELLO").name == "text.to_lower")
    assert(text.substring("abcdef", 1, 3).name == "text.substring")
    assert(time.format(time.now()).name == "time.format")
    assert(uuid.generate().name == "uuid.generate")
    assert(core.len(CelConst("items")) == CelFunc("len", CelConst("items")))
    assert(core.default(Cel.nil, "fallback").name == "default")
    assert(core.int(CelConst("amount")).name == "int")
    assert(core.string(CelConst("amount")).name == "string")
    assert(core.get_type(CelConst("amount")).name == "get_type")

    val arithmetic: Cel = CelConst("1") + CelConst("2")
    assert(arithmetic == CelOp(CelConst("1"), "+", CelConst("2")))
    val call = Call[Map[String, String], String]("http", CallOperations.HttpPost,
      obj(Map("url" -> "https://example.org")))
    assert(call.call == "http.post")
    assert(CallOperations.WorkflowRun !=
      "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run")
    assert(CelFunctions.ClockNow == CelFunctions.Time.Now)
    assert(CelFunctions.Length == "len")
    println("Standard-library names preserve existing CelFunc/CelOp and generic Call")
  }
}
