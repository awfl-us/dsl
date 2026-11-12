package us.awfl.dsl

/** Standard-library function names are the CEL names, not backend-specific
  * connector identifiers. Both compilers interpret the same CelFunc AST.
  *
  * Connection operations in CallOperations remain backend-neutral identifiers.
  */
object CelFunctions {
  object Core {
    final val Default = "default"
    final val TypeOf = "get_type"
    final val ToInt = "int"
    final val ToString = "string"
    final val Length = "len"
  }
  object Map {
    final val Get = "map.get"
    final val Merge = "map.merge"
  }
  object List {
    final val Concat = "list.concat"
  }
  object Json {
    final val Decode = "json.decode"
    final val Encode = "json.encode"
    final val EncodeToString = "json.encode_to_string"
  }
  object Base64 {
    final val Encode = "base64.encode"
  }
  object Text {
    final val Encode = "text.encode"
    final val Decode = "text.decode"
    final val Split = "text.split"
    final val Substring = "text.substring"
    final val ToLower = "text.to_lower"
    final val UrlEncode = "text.url_encode"
  }
  object Time {
    final val Now = "sys.now"
    final val Format = "time.format"
  }
  object Uuid {
    final val Generate = "uuid.generate"
  }
  object Environment {
    final val Get = "sys.get_env"
  }

  // Compatibility aliases: existing compiled workflows and Rust frontend tests
  // can keep referring to the old constant identifiers while migrating to the
  // nested, standard-library-shaped API. Values are now standard CEL names.
  final val Default = Core.Default
  final val TypeOf = Core.TypeOf
  final val ToInt = Core.ToInt
  final val ToString = Core.ToString
  final val Length = Core.Length
  final val MapGet = Map.Get
  final val MapMerge = Map.Merge
  final val ListConcat = List.Concat
  final val JsonDecode = Json.Decode
  final val JsonEncodeBytes = Json.Encode
  final val JsonEncodeString = Json.EncodeToString
  final val Base64Encode = Base64.Encode
  final val TextEncode = Text.Encode
  final val TextDecode = Text.Decode
  final val TextSplit = Text.Split
  final val TextSubstring = Text.Substring
  final val TextLowercase = Text.ToLower
  final val TextUrlEncode = Text.UrlEncode
  final val TimeFormat = Time.Format
  final val UuidGenerate = Uuid.Generate
  final val ClockNow = Time.Now
  final val EnvironmentGet = Environment.Get
}

/** Internal operation identifiers for Call/connector steps only.
  * Native and Google backends map these independently.
  */
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
