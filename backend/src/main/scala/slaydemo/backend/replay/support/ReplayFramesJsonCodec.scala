package slaydemo.backend.replay.support

import java.nio.charset.StandardCharsets
import java.util.Base64

private[replay] object ReplayFramesJsonCodec {
  def encode(value: String): String =
    Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))

  def decode(value: String): String =
    try {
      new String(Base64.getDecoder.decode(Option(value).getOrElse("")), StandardCharsets.UTF_8)
    } catch {
      case _: IllegalArgumentException => "[]"
    }
}
