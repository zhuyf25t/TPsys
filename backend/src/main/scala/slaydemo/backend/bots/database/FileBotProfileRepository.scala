package slaydemo.backend.bots.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slaydemo.backend.bots.objects.{BotId, BotProfileRecord, DemoBotProfiles}
import slaydemo.backend.shared.database.AtomicFileWrite

final class FileBotProfileRepository(storagePath: Path) extends BotProfileRepository {
  private val lock = Object()
  private var profilesById: Map[BotId, BotProfileRecord] = Map.empty

  loadFromDisk()

  override def list(): Vector[BotProfileRecord] =
    lock.synchronized {
      profilesById.values.toVector.sortBy(profile => (profile.profileOrder.value, profile.botId.value))
    }

  override def save(record: BotProfileRecord): BotProfileRecord = {
    lock.synchronized {
      profilesById = profilesById.updated(record.botId, record)
      persist()
    }
    record
  }

  private def loadFromDisk(): Unit =
    lock.synchronized {
      if !Files.exists(storagePath) then {
        seedDefaults()
        persist()
      } else {
        val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
        if raw.isEmpty then {
          seedDefaults()
          persist()
        } else {
          val parsedProfiles = BotProfileFileJsonParser.parseProfiles(raw)

          if parsedProfiles.isEmpty then {
            seedDefaults()
            persist()
          } else {
            profilesById = parsedProfiles.map(profile => profile.botId -> profile).toMap
          }
        }
      }
    }

  private def seedDefaults(): Unit =
    profilesById = DemoBotProfiles.all.map(profile => profile.botId -> profile).toMap

  private def persist(): Unit = {
    val payload = BotProfileFileJsonRenderer.renderPayload(list())
    AtomicFileWrite.writeUtf8(storagePath, payload)
  }

}

object FileBotProfileRepository {
  def apply(storagePath: Path): FileBotProfileRepository =
    new FileBotProfileRepository(storagePath)
}
