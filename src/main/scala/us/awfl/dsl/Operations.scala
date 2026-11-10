package us.awfl.dsl

/** Stable backend-independent identities. Google spellings are compatibility metadata,
  * not the serialized identity of an operation in a native workflow artifact.
  */
sealed trait OperationId {
  def wireId: String
  def googleName: String
}

enum FunctionId(val wireId: String, val googleName: String) extends OperationId {
  case Default          extends FunctionId("value.default", "default")
  case TypeOf           extends FunctionId("value.type", "get_type")
  case ToInt            extends FunctionId("value.to_int", "int")
  case ToString         extends FunctionId("value.to_string", "string")
  case Length           extends FunctionId("value.length", "len")
  case MapGet           extends FunctionId("map.get", "map.get")
  case MapMerge         extends FunctionId("map.merge", "map.merge")
  case ListAppend       extends FunctionId("list.append", "list.concat")
  case JsonDecode       extends FunctionId("json.decode", "json.decode")
  case JsonEncodeBytes  extends FunctionId("json.encode_bytes", "json.encode")
  case JsonEncodeString extends FunctionId("json.encode_string", "json.encode_to_string")
  case Base64Encode     extends FunctionId("bytes.base64_encode", "base64.encode")
  case TextEncode       extends FunctionId("text.encode", "text.encode")
  case TextDecode       extends FunctionId("text.decode", "text.decode")
  case TextSplit        extends FunctionId("text.split", "text.split")
  case TextSubstring    extends FunctionId("text.substring", "text.substring")
  case TextLowercase    extends FunctionId("text.lowercase", "text.to_lower")
  case TextUrlEncode    extends FunctionId("text.url_encode", "text.url_encode")
  case TimeFormat       extends FunctionId("time.format", "time.format")
  case UuidGenerate     extends FunctionId("uuid.generate", "uuid.generate")
  case ClockNow         extends FunctionId("clock.now", "sys.now")
  case EnvironmentGet   extends FunctionId("environment.get", "sys.get_env")
}
object FunctionId {
  private val byWire = values.map(v => v.wireId -> v).toMap
  private val byLegacy = values.map(v => v.googleName -> v).toMap
  def fromWire(value: String): Option[FunctionId] = byWire.get(value)
  def fromLegacy(value: String): Option[FunctionId] = byLegacy.get(value)
}

enum CallId(val wireId: String, val googleName: String) extends OperationId {
  case HttpGet         extends CallId("http.get", "http.get")
  case HttpPost        extends CallId("http.post", "http.post")
  case HttpPatch       extends CallId("http.patch", "http.patch")
  case HttpDelete      extends CallId("http.delete", "http.delete")
  case LogWrite        extends CallId("log.write", "sys.log")
  case CallbackCreate  extends CallId("callback.create", "events.create_callback_endpoint")
  case CallbackAwait   extends CallId("callback.await", "events.await_callback")
  case WorkflowRun     extends CallId("workflow.run", "googleapis.workflowexecutions.v1.projects.locations.workflows.executions.run")
  case StorageGet      extends CallId("storage.object_get", "googleapis.storage.v1.objects.get")
  case MessagingPublish extends CallId("messaging.publish", "googleapis.pubsub.v1.projects.topics.publish")
}
object CallId {
  private val byWire = values.map(v => v.wireId -> v).toMap
  private val byLegacy = values.map(v => v.googleName -> v).toMap
  def fromWire(value: String): Option[CallId] = byWire.get(value)
  def fromLegacy(value: String): Option[CallId] = byLegacy.get(value)
}

enum GlobalId(val wireId: String, val googleEnvironmentVariable: String) {
  case WorkflowId  extends GlobalId("workflow.id", "GOOGLE_CLOUD_WORKFLOW_ID")
  case ExecutionId extends GlobalId("execution.id", "GOOGLE_CLOUD_WORKFLOW_EXECUTION_ID")
  case CloudProjectId extends GlobalId("cloud.project_id", "GOOGLE_CLOUD_PROJECT_ID")
  case Region      extends GlobalId("cloud.region", "GOOGLE_CLOUD_LOCATION")
}

enum OperatorId(val wireId: String, val googleSymbol: String, val arity: Int, val shortCircuit: Boolean = false) {
  case Equal       extends OperatorId("compare.eq", "==", 2)
  case NotEqual    extends OperatorId("compare.ne", "!=", 2)
  case Greater     extends OperatorId("compare.gt", ">", 2)
  case GreaterEq   extends OperatorId("compare.gte", ">=", 2)
  case Less        extends OperatorId("compare.lt", "<", 2)
  case LessEq      extends OperatorId("compare.lte", "<=", 2)
  case Add         extends OperatorId("math.add", "+", 2)
  case Subtract    extends OperatorId("math.subtract", "-", 2)
  case Multiply    extends OperatorId("math.multiply", "*", 2)
  case DivideInt   extends OperatorId("math.divide_int", "//", 2)
  case Contains    extends OperatorId("collection.in", "in", 2)
  case And         extends OperatorId("boolean.and", "and", 2, true)
  case Or          extends OperatorId("boolean.or", "or", 2, true)
  case Not         extends OperatorId("boolean.not", "not", 1)
}
object OperatorId {
  def fromLegacy(symbol: String, arity: Int): Option[OperatorId] =
    values.find(op => op.googleSymbol == symbol && op.arity == arity)
}

enum RetryPolicyId(val wireId: String) {
  case NoRetry extends RetryPolicyId("retry.none")
  case LegacyHttpDefault extends RetryPolicyId("retry.legacy_http_default")
}

enum ValueKind {
  case AnyValue, NullValue, Bool, Integer, Floating, Text, Bytes, Sequence, Object
}
enum EffectKind {
  case Network, Log, Callback, ChildWorkflow, Storage, Messaging
}

/** Version is absent until native semantics and conformance tests exist. */
case class FunctionSpec(
  id: FunctionId,
  minArity: Int,
  maxArity: Int,
  acceptedArguments: List[Set[ValueKind]],
  result: ValueKind,
  deterministic: Boolean,
  errors: String,
  rustAbi: Option[Int] = None
) {
  require(minArity >= 0 && maxArity >= minArity)
  def acceptsArity(count: Int): Boolean = count >= minArity && count <= maxArity
}
case class CallSpec(
  id: CallId,
  argumentShape: ValueKind,
  result: ValueKind,
  effect: EffectKind,
  errors: String,
  legacyRetry: RetryPolicyId = RetryPolicyId.LegacyHttpDefault,
  rustAbi: Option[Int] = None
)

/** Canonical operation signatures. Keep Google aliases here, never scattered across emitters. */
object OperationRegistry {
  import ValueKind.*
  private val any = Set(AnyValue)
  private val text = Set(Text)
  private val objectKind = Set(Object)
  private val sequence = Set(Sequence)
  private def fn(id: FunctionId, min: Int, max: Int, args: List[Set[ValueKind]], out: ValueKind,
                 errors: String, deterministic: Boolean = true): FunctionSpec =
    FunctionSpec(id, min, max, args, out, deterministic, errors)

  val functions: Map[FunctionId, FunctionSpec] = List(
    fn(FunctionId.Default, 2, 2, List(any, any), AnyValue, "missing/null defaults"),
    fn(FunctionId.TypeOf, 1, 1, List(any), Text, "Google type label including bytes"),
    fn(FunctionId.ToInt, 1, 1, List(any), Integer, "conversion and range error"),
    fn(FunctionId.ToString, 1, 1, List(any), Text, "conversion and formatting error"),
    fn(FunctionId.Length, 1, 1, List(Set(Text, Bytes, Sequence, Object)), Integer, "unsupported length type"),
    fn(FunctionId.MapGet, 2, 3, List(objectKind, any, any), AnyValue, "missing and null distinct"),
    fn(FunctionId.MapMerge, 2, 2, List(objectKind, objectKind), Object, "shallow merge and invalid map"),
    fn(FunctionId.ListAppend, 2, 2, List(sequence, any), Sequence, "append one item, no flattening"),
    fn(FunctionId.JsonDecode, 1, 1, List(Set(Text, Bytes)), AnyValue, "invalid JSON and number conversion"),
    fn(FunctionId.JsonEncodeBytes, 1, 1, List(any), Bytes, "JSON encoding error"),
    fn(FunctionId.JsonEncodeString, 1, 1, List(any), Text, "JSON encoding error"),
    fn(FunctionId.Base64Encode, 1, 1, List(Set(Bytes)), Text, "invalid byte input"),
    fn(FunctionId.TextEncode, 1, 2, List(text, text), Bytes, "unknown encoding"),
    fn(FunctionId.TextDecode, 1, 2, List(Set(Bytes), text), Text, "invalid bytes or encoding"),
    fn(FunctionId.TextSplit, 2, 2, List(text, text), Sequence, "separator and Unicode compatibility"),
    fn(FunctionId.TextSubstring, 2, 3, List(text, Set(Integer), Set(Integer)), Text, "index bounds"),
    fn(FunctionId.TextLowercase, 1, 1, List(text), Text, "Unicode mapping"),
    fn(FunctionId.TextUrlEncode, 1, 1, List(text), Text, "URL encoding"),
    fn(FunctionId.TimeFormat, 2, 2, List(any, text), Text, "timezone and format error"),
    fn(FunctionId.UuidGenerate, 0, 0, Nil, Text, "journal invocation result", false),
    fn(FunctionId.ClockNow, 0, 0, Nil, Floating, "journal invocation result", false),
    fn(FunctionId.EnvironmentGet, 1, 2, List(text, any), AnyValue, "only allowlisted trusted environment", false)
  ).map(s => s.id -> s).toMap

  private def call(id: CallId, effect: EffectKind, result: ValueKind, errors: String): CallSpec =
    CallSpec(id, Object, result, effect, errors)

  val calls: Map[CallId, CallSpec] = List(
    call(CallId.HttpGet, EffectKind.Network, Object, "HTTP code/body/headers and transport failures"),
    call(CallId.HttpPost, EffectKind.Network, Object, "HTTP code/body/headers and unknown outcome"),
    call(CallId.HttpPatch, EffectKind.Network, Object, "HTTP code/body/headers and unknown outcome"),
    call(CallId.HttpDelete, EffectKind.Network, Object, "HTTP code/body/headers and unknown outcome"),
    call(CallId.LogWrite, EffectKind.Log, AnyValue, "logging failure"),
    call(CallId.CallbackCreate, EffectKind.Callback, Object, "callback creation failure"),
    call(CallId.CallbackAwait, EffectKind.Callback, Object, "timeout, cancellation and duplicate delivery"),
    call(CallId.WorkflowRun, EffectKind.ChildWorkflow, AnyValue, "child failure and async handle"),
    call(CallId.StorageGet, EffectKind.Storage, Object, "Google storage response and error envelope"),
    call(CallId.MessagingPublish, EffectKind.Messaging, Object, "Google Pub/Sub response and error envelope")
  ).map(s => s.id -> s).toMap

  def requireNative(id: FunctionId, abi: Int, context: String): Either[String, FunctionSpec] =
    functions.get(id) match {
      case Some(s) if s.rustAbi.exists(_ <= abi) => Right(s)
      case Some(_) => Left(context + ": function " + id.wireId + " has no tested Rust ABI implementation")
      case None => Left(context + ": unknown function " + id.wireId)
    }

  def requireNative(id: CallId, abi: Int, context: String): Either[String, CallSpec] =
    calls.get(id) match {
      case Some(s) if s.rustAbi.exists(_ <= abi) => Right(s)
      case Some(_) => Left(context + ": call " + id.wireId + " has no tested Rust ABI implementation")
      case None => Left(context + ": unknown call " + id.wireId)
    }

  def resolveLegacyFunction(name: String, context: String): Either[String, FunctionId] =
    FunctionId.fromLegacy(name).toRight(context + ": unclassified CEL function '" + name + "' (YAML-only; Rust requires a registered FunctionId)")

  def resolveLegacyCall(name: String, context: String): Either[String, CallId] =
    CallId.fromLegacy(name).toRight(context + ": unclassified call '" + name + "' (YAML-only; Rust requires a registered CallId)")
}
