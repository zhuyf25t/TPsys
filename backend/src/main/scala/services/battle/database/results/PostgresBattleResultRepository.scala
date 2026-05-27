package services.battle.database.results

import services.battle.objects.{BattleId, BattleResultRecord}
import services.identity.objects.PlayerHandle
import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

final class PostgresBattleResultRepository(settings: PostgresConnectionSettings) extends BattleResultRepository {
  PostgresSupport.withConnection(settings)(BattleResultTableInitializer.initialize)

  override def save(record: BattleResultRecord): BattleResultRecord = {
    PostgresSupport.withTransactionConnection(settings) { connection =>
      BattleResultTable.save(connection, record)
    }
  }

  override def list(
    handle: Option[PlayerHandle],
    battleId: Option[BattleId],
    limit: Int
  ): Vector[BattleResultRecord] = {
    PostgresSupport.withConnection(settings) { connection =>
      BattleResultTable.list(connection, handle, battleId, limit)
    }
  }
}
