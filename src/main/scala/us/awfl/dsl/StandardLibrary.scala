package us.awfl.dsl

/** Standard-library expressions. Each helper produces an ordinary CelFunc,
  * preserving the existing DSL expression AST and the original library name.
  *
  * An `import us.awfl.dsl.*` imports these modules directly, so workflows
  * can write `time.now()`, `map.get(...)`, `text.to_lower(...)`, etc.
  */
object map {
  def get(source: Cel, keys: Cel): CelFunc = CelFunc(CelFunctions.Map.Get, source, keys)
  def merge(left: Cel, right: Cel): CelFunc = CelFunc(CelFunctions.Map.Merge, left, right)
}

object list {
  def concat(source: Cel, element: Cel): CelFunc = CelFunc(CelFunctions.List.Concat, source, element)
}

object json {
  def decode(source: Cel): CelFunc = CelFunc(CelFunctions.Json.Decode, source)
  def encode(source: Cel): CelFunc = CelFunc(CelFunctions.Json.Encode, source)
  def encode_to_string(source: Cel): CelFunc = CelFunc(CelFunctions.Json.EncodeToString, source)
}

object base64 {
  def encode(data: Cel): CelFunc = CelFunc(CelFunctions.Base64.Encode, data)
  def encode(data: Cel, padding: Cel): CelFunc = CelFunc(CelFunctions.Base64.Encode, data, padding)
}

object text {
  def encode(source: Cel): CelFunc = CelFunc(CelFunctions.Text.Encode, source)
  def encode(source: Cel, charset: Cel): CelFunc = CelFunc(CelFunctions.Text.Encode, source, charset)
  def decode(source: Cel): CelFunc = CelFunc(CelFunctions.Text.Decode, source)
  def decode(source: Cel, charset: Cel): CelFunc = CelFunc(CelFunctions.Text.Decode, source, charset)
  def split(source: Cel, separator: Cel): CelFunc = CelFunc(CelFunctions.Text.Split, source, separator)
  def substring(source: Cel, start: Cel): CelFunc = CelFunc(CelFunctions.Text.Substring, source, start)
  def substring(source: Cel, start: Cel, end: Cel): CelFunc = CelFunc(CelFunctions.Text.Substring, source, start, end)
  def to_lower(source: Cel): CelFunc = CelFunc(CelFunctions.Text.ToLower, source)
  def url_encode(source: Cel): CelFunc = CelFunc(CelFunctions.Text.UrlEncode, source)
}

object time {
  /** Workflows CEL calls this sys.now(); use time.now() in Scala so we never
    * shadow scala.sys while retaining the exact native library identifier.
    */
  def now(): CelFunc = CelFunc(CelFunctions.Time.Now)
  def format(seconds: Cel): CelFunc = CelFunc(CelFunctions.Time.Format, seconds)
  def format(seconds: Cel, timezone: Cel): CelFunc = CelFunc(CelFunctions.Time.Format, seconds, timezone)
}

object uuid {
  def generate(): CelFunc = CelFunc(CelFunctions.Uuid.Generate)
}

object environment {
  def get(name: Cel): CelFunc = CelFunc(CelFunctions.Environment.Get, name)
  def get(name: Cel, default: Cel): CelFunc = CelFunc(CelFunctions.Environment.Get, name, default)
}

/** Unqualified expression helpers live in a separate namespace so they do not
  * conflict with the existing DSL `len` and Scala methods/types.
  */
object core {
  def default(value: Cel, fallback: Cel): CelFunc = CelFunc(CelFunctions.Core.Default, value, fallback)
  def get_type(value: Cel): CelFunc = CelFunc(CelFunctions.Core.TypeOf, value)
  def int(value: Cel): CelFunc = CelFunc(CelFunctions.Core.ToInt, value)
  def string(value: Cel): CelFunc = CelFunc(CelFunctions.Core.ToString, value)
  def len(value: Cel): CelFunc = CelFunc(CelFunctions.Core.Length, value)
}
