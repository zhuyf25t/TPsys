package services.identity.objects

import java.util.Locale

import system.objects.UserId
import system.policies.HandlePolicy

final case class PlayerHandle(value: String) extends AnyVal {
  def key: String = value.toLowerCase(Locale.ROOT)
}

object PlayerHandle {
  private val HandlePattern = "^[a-zA-Z0-9_-]+$".r

  def forRegistration(value: String): Option[PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    Option
      .when(
        HandlePolicy.isPlayableIdentityHandle(trimmed) &&
          trimmed.length >= 3 &&
          trimmed.length <= 16 &&
          HandlePattern.matches(trimmed)
      )(PlayerHandle(trimmed))
  }

  def forLookup(value: String): Option[PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    Option.when(HandlePolicy.isPlayableIdentityHandle(trimmed))(PlayerHandle(trimmed))
  }
}

final case class DisplayName(value: String) extends AnyVal

enum SkinId {
  case Blue
  case Survivor
  case Soldier
  case Old
  case Zombie
}

object SkinId {
  def fromString(value: String): Option[SkinId] = {
    val normalized = Option(value).getOrElse("").trim.toLowerCase(Locale.ROOT)
    normalized match {
      case "blue"     => Some(SkinId.Blue)
      case "survivor" => Some(SkinId.Survivor)
      case "soldier"  => Some(SkinId.Soldier)
      case "old"      => Some(SkinId.Old)
      case "zombie"   => Some(SkinId.Zombie)
      case _          => None
    }
  }

  def wireValue(value: SkinId): String =
    value match {
      case SkinId.Blue     => "blue"
      case SkinId.Survivor => "survivor"
      case SkinId.Soldier  => "soldier"
      case SkinId.Old      => "old"
      case SkinId.Zombie   => "zombie"
    }
}

final case class SessionToken(value: String) extends AnyVal

object SessionToken {
  def fromString(value: String): Option[SessionToken] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(SessionToken.apply)
}

final class PlainTextPassword private (val value: String) {
  override def toString: String = "<redacted>"
}

object PlainTextPassword {
  def fromString(value: String): Option[PlainTextPassword] =
    Option(value).map(_.trim).filter(_.length >= 4).map(new PlainTextPassword(_))

  def unsafe(value: String): PlainTextPassword =
    fromString(value).getOrElse {
      throw IllegalArgumentException("Password must contain at least four characters.")
    }
}

final class PasswordHash private (val value: String) {
  override def toString: String = "<redacted>"
}

object PasswordHash {
  def fromString(value: String): Option[PasswordHash] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(new PasswordHash(_))

  def unsafe(value: String): PasswordHash =
    fromString(value).getOrElse {
      throw IllegalArgumentException("Password hash must be non-empty.")
    }
}

enum AccountStatus {
  case Active
}

final case class IdentityAccount(
  userId: UserId,
  handle: PlayerHandle,
  displayName: DisplayName,
  skinId: SkinId,
  sessionToken: Option[SessionToken],
  status: AccountStatus
)

final case class IdentityAccountSummary(
  handle: PlayerHandle,
  displayName: DisplayName,
  skinId: SkinId
)

object IdentityAccount {
  def active(
    userId: UserId,
    handle: PlayerHandle,
    skinId: SkinId,
    sessionToken: Option[SessionToken]
  ): IdentityAccount =
    IdentityAccount(
      userId = userId,
      handle = handle,
      displayName = DisplayName(handle.value),
      skinId = skinId,
      sessionToken = sessionToken,
      status = AccountStatus.Active
    )
}
