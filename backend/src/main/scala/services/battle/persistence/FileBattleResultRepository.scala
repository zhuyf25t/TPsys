package services.battle.persistence

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import services.battle.objects.{
  BattleId,
  BattleResultId,
  BattleResultRecord,
}
import services.identity.objects.PlayerHandle
import system.database.AtomicFileWrite

final class FileBattleResultRepository(storagePath: Path) extends BattleResultRepository {
  private val lock = Object()
  private var recordsById: Map[BattleResultId, BattleResultRecord] = Map.empty

  loadFromDisk()

  override def save(record: BattleResultRecord): BattleResultRecord = {
    lock.synchronized {
      recordsById = recordsById.updated(record.resultId, record)
      persist()
    }
    record
  }

  override def list(
    handle: Option[PlayerHandle],
    battleId: Option[BattleId],
    limit: Int
  ): Vector[BattleResultRecord] =
    lock.synchronized {
      recordsById.values.toVector
    }.filter(record => handle.forall(_.key == record.handle.key))
      .filter(record => battleId.forall(_.value == record.battleId.value))
      .sortWith(BattleResultRepositoryOrderingRules.recentFirst)
      .take(math.max(0, limit))

  private def loadFromDisk(): Unit =
    lock.synchronized {
      if Files.exists(storagePath) then {
        val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
        if raw.nonEmpty then {
          recordsById = BattleResultFileJsonParser
            .parseRecords(raw)
            .map(record => record.resultId -> record)
            .toMap
        }
      }
    }

  private def persist(): Unit = {
    val payload = BattleResultFileJsonRenderer.renderPayload(
      recordsById.values.toVector.sortWith(BattleResultRepositoryOrderingRules.recentFirst)
    )
    AtomicFileWrite.writeUtf8(storagePath, payload)
  }
}

object FileBattleResultRepository {
  def apply(storagePath: Path): FileBattleResultRepository =
    new FileBattleResultRepository(storagePath)
}
