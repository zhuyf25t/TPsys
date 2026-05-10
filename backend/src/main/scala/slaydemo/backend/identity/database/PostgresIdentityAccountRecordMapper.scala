package slaydemo.backend.identity.database

import java.sql.{PreparedStatement, ResultSet}

import slaydemo.backend.identity.objects.{AccountStatus, DisplayName, IdentityAccount, PasswordHash, PlayerHandle, SessionToken, SkinId}
import slaydemo.backend.shared.objects.UserId

private[database] object PostgresIdentityAccountRecordMapper {
  def bindCreate(statement: PreparedStatement, account: IdentityAccount, passwordHash: PasswordHash): Unit = {
    statement.setString(1, account.userId.value)
    statement.setString(2, account.handle.value)
    statement.setString(3, account.displayName.value)
    statement.setString(4, SkinId.wireValue(account.skinId))
    statement.setString(5, account.sessionToken.map(_.value).getOrElse(""))
    statement.setString(6, passwordHash.value)
  }

  def readOptionalAccount(resultSet: ResultSet): Option[IdentityAccount] =
    if (resultSet.next()) Some(readAccount(resultSet)) else None

  def readAccounts(resultSet: ResultSet): Vector[IdentityAccount] = {
    val accounts = Vector.newBuilder[IdentityAccount]
    while (resultSet.next()) {
      accounts += readAccount(resultSet)
    }
    accounts.result()
  }

  def readAccount(resultSet: ResultSet): IdentityAccount =
    IdentityAccount(
      userId = UserId(resultSet.getString("user_id")),
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      skinId = SkinId.fromString(resultSet.getString("skin_id")).getOrElse(SkinId.Blue),
      sessionToken = SessionToken.fromString(resultSet.getString("session_token")),
      status = AccountStatus.Active
    )
}
