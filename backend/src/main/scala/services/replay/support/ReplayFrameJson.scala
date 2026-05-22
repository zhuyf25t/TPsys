package services.replay.support

import services.replay.objects.ReplayFramesJson

object ReplayFrameJson {
  enum Error {
    case InvalidFramesJson
  }

  final case class Normalized(
    framesJson: ReplayFramesJson,
    frameCount: Int
  ) {
    def playbackAvailable: Boolean =
      frameCount >= 2
  }

  def normalize(value: String): Either[Error, Normalized] = {
    val trimmed = Option(value).getOrElse("").trim
    if trimmed.isEmpty || trimmed == "null" then Right(Normalized(ReplayFramesJson.empty, 0))
    else
      ReplayJsonArrayCounter.count(trimmed)
        .map(count => Right(Normalized(ReplayFramesJson.fromNormalized(trimmed), count)))
        .getOrElse(Left(Error.InvalidFramesJson))
  }
}
