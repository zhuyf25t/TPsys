package slaydemo.backend.identity.ports

trait PasswordHashPort {
  def verify(password: String, passwordHash: String): Boolean
}
