package slaydemo.backend.identity.ports

import java.security.SecureRandom

import slaydemo.backend.identity.objects.{PasswordHash, PlainTextPassword}

object PasswordHasherContractTest {
  def main(args: Array[String]): Unit = {
    pbkdf2HashVerifiesPassword()
    pbkdf2HashUsesDifferentSalt()
    pbkdf2HasherVerifiesLegacySha256Hash()
    structuredHashIsDetected()

    println("PasswordHasher contract checks passed")
  }

  private def pbkdf2HashVerifiesPassword(): Unit = {
    val hasher = Pbkdf2PasswordHasher(secureRandom = deterministicRandom())
    val password = PlainTextPassword.unsafe("correct horse battery staple")
    val hash = hasher.hash(password)

    assertEquals("pbkdf2 verifies matching password", hasher.verify(password, hash), PasswordVerification.Verified)
    assertEquals(
      "pbkdf2 rejects wrong password",
      hasher.verify(PlainTextPassword.unsafe("wrong"), hash),
      PasswordVerification.Rejected
    )
    assertEquals("pbkdf2 hash does not need rehash", hasher.needsRehash(hash), false)
  }

  private def pbkdf2HashUsesDifferentSalt(): Unit = {
    val hasher = Pbkdf2PasswordHasher()
    val password = PlainTextPassword.unsafe("same-password")

    assertEquals("pbkdf2 hash is salted", hasher.hash(password).value == hasher.hash(password).value, false)
  }

  private def pbkdf2HasherVerifiesLegacySha256Hash(): Unit = {
    val password = PlainTextPassword.unsafe("legacy-password")
    val legacyHash = Sha256PasswordHasher().hash(password)
    val hasher = Pbkdf2PasswordHasher(secureRandom = deterministicRandom())

    assertEquals("legacy sha256 verifies", hasher.verify(password, legacyHash), PasswordVerification.Verified)
    assertEquals("legacy sha256 needs rehash", hasher.needsRehash(legacyHash), true)
  }

  private def structuredHashIsDetected(): Unit = {
    val hash = Pbkdf2PasswordHasher(secureRandom = deterministicRandom()).hash(PlainTextPassword.unsafe("password"))

    assertEquals("pbkdf2 structured hash is detected", Pbkdf2PasswordHasher.isStructuredHash(hash), true)
    assertEquals("plain text is not a structured hash", Pbkdf2PasswordHasher.isStructuredHash(PasswordHash.unsafe("password")), false)
  }

  private def deterministicRandom(): SecureRandom = {
    val random = SecureRandom.getInstance("SHA1PRNG")
    random.setSeed(7L)
    random
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
