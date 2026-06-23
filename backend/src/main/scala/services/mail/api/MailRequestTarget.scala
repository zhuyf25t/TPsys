package services.mail.api

object MailRequestTarget {
  private val MailListPaths: Set[String] =
    Set("/mails", "/api/mails")
  private val MailReadPaths: Set[String] =
    Set("/mails/read", "/api/mails/read")

  def isListPath(path: String): Boolean =
    MailListPaths.contains(path)

  def isReadPath(path: String): Boolean =
    MailReadPaths.contains(path)
}
