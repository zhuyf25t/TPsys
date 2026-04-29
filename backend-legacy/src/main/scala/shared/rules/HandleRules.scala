package slaydemo.backend.shared.rules

import java.util.Locale

object HandleRules {
  private val visitorLikeHandleKeys = Set(
    "visitor",
    "guest",
    "anonymous",
    "anon",
    "访客",
    "游客",
    "未登录"
  )

  def normalizeKey(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase(Locale.ROOT)

  def isVisitorLikeHandle(value: String): Boolean =
    visitorLikeHandleKeys.contains(normalizeKey(value))

  def isPlayableIdentityHandle(value: String): Boolean = {
    val normalized = normalizeKey(value)
    normalized.nonEmpty && !visitorLikeHandleKeys.contains(normalized)
  }
}
