package slaydemo.backend.governance.ports

trait AuditLogPort {
  def writeAuditEntry(entryType: String, payload: String): Unit
}
