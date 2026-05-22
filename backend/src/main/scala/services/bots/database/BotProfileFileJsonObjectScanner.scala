package services.bots.database

import scala.annotation.tailrec

private[database] object BotProfileFileJsonObjectScanner {
  def extractProfileObjects(raw: String): Vector[String] = {
    val marker = raw.indexOf("\"profiles\"")
    if marker < 0 then Vector.empty
    else {
      val start = raw.indexOf('[', marker)
      val end = raw.lastIndexOf(']')
      if start < 0 || end < 0 || end <= start then Vector.empty
      else scanProfileObjects(raw.substring(start + 1, end), ProfileScanState.initial).chunks
    }
  }

  @tailrec
  private def scanProfileObjects(section: String, state: ProfileScanState): ProfileScanState =
    if state.index >= section.length then state
    else {
      val char = section.charAt(state.index)
      val nextState =
        if state.inString then {
          if state.escaped then state.copy(escaped = false)
          else if char == '\\' then state.copy(escaped = true)
          else if char == '"' then state.copy(inString = false)
          else state
        } else {
          char match {
            case '"' =>
              state.copy(inString = true)
            case '{' =>
              state.copy(
                depth = state.depth + 1,
                start = if state.depth == 0 then state.index else state.start
              )
            case '}' =>
              val nextDepth = state.depth - 1
              if nextDepth == 0 && state.start >= 0 then
                state.copy(
                  depth = nextDepth,
                  start = -1,
                  chunks = state.chunks :+ section.substring(state.start, state.index + 1)
                )
              else state.copy(depth = nextDepth)
            case _ =>
              state
          }
        }

      scanProfileObjects(section, nextState.copy(index = state.index + 1))
    }
}

private final case class ProfileScanState(
  depth: Int,
  inString: Boolean,
  escaped: Boolean,
  start: Int,
  index: Int,
  chunks: Vector[String]
)

private object ProfileScanState {
  val initial: ProfileScanState =
    ProfileScanState(
      depth = 0,
      inString = false,
      escaped = false,
      start = -1,
      index = 0,
      chunks = Vector.empty
    )
}
