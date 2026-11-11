package us.awfl.dsl

/** Backend-neutral operation identifiers. These are ordinary strings so the
  * existing CelFunc(name, args*) and Call(name, call, args) constructors stay
  * unchanged. Google/YAML and native Rust compilers map them independently.
  * No additional CEL AST variants or typed Call classes are required.
  */
object CelFunctions {
  final val Default = "value.default"
  final val TypeOf = "value.type"
  final val ToInt = "value.to_int"
  final val ToString = "value.to_string"
  final val Length = "value.length"
  final val MapGet = "map.get"
  final val MapMerge = "map.merge"
  final val ListConcat = "list.concat"
  final val JsonDecode = "json.decode"
  final val JsonEncodeBytes = "json.encode_bytes"
  final val JsonEncodeString = "json.encode_string"
  final val Base64Encode = "bytes.base64_encode"
  final val TextEncode = "text.encode"
  final val TextDecode = "text.decode"
  final val TextSplit = "text.split"
  final val TextSubstring = "text.substring"
  final val TextLowercase = "text.lowercase"
  final val TextUrlEncode = "text.url_encode"
  final val TimeFormat = "time.format"
  final val UuidGenerate = "uuid.generate"
  final val ClockNow = "clock.now"
  final val EnvironmentGet = "environment.get"
}

object CallOperations {
  final val HttpGet = "http.get"
  final val HttpPost = "http.post"
  final val HttpPatch = "http.patch"
  final val HttpDelete = "http.delete"
  final val LogWrite = "log.write"
  final val CallbackCreate = "callback.create"
  final val CallbackAwait = "callback.await"
  final val WorkflowRun = "workflow.run"
  final val StorageGet = "storage.object_get"
  final val MessagingPublish = "messaging.publish"
}
