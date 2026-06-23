package services.identity.api

object IdentityRequestTarget {
  private val RegisterPaths: Set[String] =
    Set("/identity/register", "/api/identity/register")
  private val SessionPaths: Set[String] =
    Set("/identity/session", "/api/identity/session")
  private val CurrentPaths: Set[String] =
    Set("/identity/me", "/api/identity/me")
  private val AccountsPaths: Set[String] =
    Set("/identity/accounts", "/api/identity/accounts")

  def isRegisterPath(path: String): Boolean =
    RegisterPaths.contains(path)

  def isSessionPath(path: String): Boolean =
    SessionPaths.contains(path)

  def isCurrentPath(path: String): Boolean =
    CurrentPaths.contains(path)

  def isAccountsPath(path: String): Boolean =
    AccountsPaths.contains(path)
}
