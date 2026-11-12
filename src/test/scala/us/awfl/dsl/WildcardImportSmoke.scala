package awfl.external

import us.awfl.dsl.*

/** Verify the public, single-import API from outside the DSL package. */
object WildcardImportSmoke {
  def main(args: Array[String]): Unit = {
    val timestamp: Value[Double] = Value(time.now())
    val field: Cel = map.get(CelConst("payload"), "id")
    val lowered: Cel = text.to_lower(field)
    val encoded: Cel = json.encode_to_string(lowered)
    val functions = List(
      timestamp.resolver.path.path.head.asInstanceOf[CelFunc],
      field.asInstanceOf[CelFunc],
      lowered.asInstanceOf[CelFunc],
      encoded.asInstanceOf[CelFunc],
      core.int("42"),
      base64.encode("dGVzdA=="),
      uuid.generate(),
      environment.get("PROJECT_ID")
    )
    assert(functions.map(_.name) == List(
      "sys.now", "map.get", "text.to_lower", "json.encode_to_string",
      "int", "base64.encode", "uuid.generate", "sys.get_env"))
    assert(scala.sys.props != null) // The DSL must not shadow scala.sys.
    val call = Call[String, String]("send", CallOperations.HttpPost, obj("hi"))
    assert(call.call == "http.post")
    println("DSL_WILDCARD_IMPORT_PASS")
  }
}
