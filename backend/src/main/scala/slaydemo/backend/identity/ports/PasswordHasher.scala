package slaydemo.backend.identity.ports

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import slaydemo.backend.identity.objects.{PasswordHash, PlainTextPassword}

enum PasswordVerification {
  case Verified
  case Rejected
}

trait PasswordHasher {
  def hash(password: PlainTextPassword): PasswordHash

  def verify(password: PlainTextPassword, hash: PasswordHash): PasswordVerification =
    if this.hash(password).value == hash.value then PasswordVerification.Verified
    else PasswordVerification.Rejected
}

final class Sha256PasswordHasher extends PasswordHasher {
  override def hash(password: PlainTextPassword): PasswordHash = {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(password.value.getBytes(StandardCharsets.UTF_8))
    PasswordHash.unsafe(bytes.map(byte => f"${byte & 0xff}%02x").mkString)
  }
}
