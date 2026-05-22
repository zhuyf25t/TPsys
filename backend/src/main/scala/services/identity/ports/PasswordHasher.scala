package services.identity.ports

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import services.identity.objects.{PasswordHash, PlainTextPassword}

enum PasswordVerification {
  case Verified
  case Rejected
}

trait PasswordHasher {
  def hash(password: PlainTextPassword): PasswordHash

  def needsRehash(hash: PasswordHash): Boolean =
    false

  def verify(password: PlainTextPassword, hash: PasswordHash): PasswordVerification =
    if MessageDigest.isEqual(this.hash(password).value.getBytes(StandardCharsets.UTF_8), hash.value.getBytes(StandardCharsets.UTF_8)) then
      PasswordVerification.Verified
    else PasswordVerification.Rejected
}

final class Sha256PasswordHasher extends PasswordHasher {
  override def hash(password: PlainTextPassword): PasswordHash = {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(password.value.getBytes(StandardCharsets.UTF_8))
    PasswordHash.unsafe(bytes.map(byte => f"${byte & 0xff}%02x").mkString)
  }
}

final class Pbkdf2PasswordHasher(
  saltBytes: Int = Pbkdf2PasswordHasher.DefaultSaltBytes,
  iterations: Int = Pbkdf2PasswordHasher.DefaultIterations,
  keyLengthBits: Int = Pbkdf2PasswordHasher.DefaultKeyLengthBits,
  secureRandom: SecureRandom = SecureRandom()
) extends PasswordHasher {
  override def hash(password: PlainTextPassword): PasswordHash = {
    val salt = Array.ofDim[Byte](saltBytes)
    secureRandom.nextBytes(salt)
    PasswordHash.unsafe(Pbkdf2PasswordHasher.render(iterations, salt, derive(password, salt, iterations, keyLengthBits)))
  }

  override def verify(password: PlainTextPassword, hash: PasswordHash): PasswordVerification =
    Pbkdf2PasswordHasher.parse(hash) match {
      case Some(stored) =>
        val derived = derive(password, stored.salt, stored.iterations, stored.hash.length * 8)
        if MessageDigest.isEqual(derived, stored.hash) then PasswordVerification.Verified
        else PasswordVerification.Rejected
      case None =>
        Pbkdf2PasswordHasher.LegacySha256.verify(password, hash)
    }

  override def needsRehash(hash: PasswordHash): Boolean =
    Pbkdf2PasswordHasher.parse(hash).isEmpty

  private def derive(
    password: PlainTextPassword,
    salt: Array[Byte],
    iterationCount: Int,
    keyLength: Int
  ): Array[Byte] = {
    val spec = PBEKeySpec(password.value.toCharArray, salt, iterationCount, keyLength)
    try SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded
    finally spec.clearPassword()
  }
}

object Pbkdf2PasswordHasher {
  val DefaultSaltBytes: Int = 16
  val DefaultIterations: Int = 120000
  val DefaultKeyLengthBits: Int = 256

  private val Algorithm = "pbkdf2-sha256"
  private val Version = "v1"
  private val LegacySha256 = Sha256PasswordHasher()
  private val Base64Encoder = Base64.getUrlEncoder.withoutPadding()
  private val Base64Decoder = Base64.getUrlDecoder

  def isStructuredHash(hash: PasswordHash): Boolean =
    parse(hash).isDefined

  private[ports] def render(iterations: Int, salt: Array[Byte], hash: Array[Byte]): String =
    s"$$$Algorithm$$$Version$$$iterations$$${Base64Encoder.encodeToString(salt)}$$${Base64Encoder.encodeToString(hash)}"

  private def parse(hash: PasswordHash): Option[Pbkdf2StoredHash] =
    hash.value.split("\\$", -1).toList match {
      case "" :: Algorithm :: Version :: rawIterations :: rawSalt :: rawHash :: Nil =>
        for
          iterations <- rawIterations.toIntOption.filter(_ > 0)
          salt <- decodeBase64(rawSalt).filter(_.nonEmpty)
          bytes <- decodeBase64(rawHash).filter(_.nonEmpty)
        yield Pbkdf2StoredHash(iterations, salt, bytes)
      case _ =>
        None
    }

  private def decodeBase64(value: String): Option[Array[Byte]] =
    try Some(Base64Decoder.decode(value))
    catch {
      case _: IllegalArgumentException => None
    }

  private final case class Pbkdf2StoredHash(
    iterations: Int,
    salt: Array[Byte],
    hash: Array[Byte]
  )
}
