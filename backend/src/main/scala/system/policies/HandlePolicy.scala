package system.policies

import java.util.Locale

object HandlePolicy {
  private val visitorLikeHandleKeys = Set(
    "visitor",
    "guest",
    "anonymous",
    "anon",
    "\u8bbf\u5ba2",
    "\u6e38\u5ba2",
    "\u672a\u767b\u5f55"
  )

  def normalizeKey(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase(Locale.ROOT)

  def trim(value: String): String =
    Option(value).getOrElse("").trim

  def isVisitorLikeHandle(value: String): Boolean =
    visitorLikeHandleKeys.contains(normalizeKey(value))

  def isPlayableIdentityHandle(value: String): Boolean = {
    val normalized = normalizeKey(value)
    normalized.nonEmpty && !visitorLikeHandleKeys.contains(normalized)
  }
}
