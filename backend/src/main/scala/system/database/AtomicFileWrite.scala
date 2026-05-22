package system.database

import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption, StandardOpenOption}

object AtomicFileWrite {
  def writeUtf8(path: Path, payload: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))

    val tempPath = path.resolveSibling(s"${path.getFileName.toString}.tmp")
    Files.writeString(
      tempPath,
      payload,
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )

    try Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    catch {
      case _: AtomicMoveNotSupportedException =>
        Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING)
    }
  }
}
