package slaydemo.backend.replay.support

object ReplayFrameJson {
  enum Error {
    case InvalidFramesJson
  }

  final case class Normalized(
    framesJson: String,
    frameCount: Int
  ) {
    def playbackAvailable: Boolean =
      frameCount >= 2
  }

  def normalize(value: String): Either[Error, Normalized] = {
    val trimmed = Option(value).getOrElse("").trim
    if trimmed.isEmpty || trimmed == "null" then Right(Normalized("[]", 0))
    else
      JsonArrayCounter.count(trimmed)
        .map(count => Right(Normalized(trimmed, count)))
        .getOrElse(Left(Error.InvalidFramesJson))
  }

  private object JsonArrayCounter {
    def count(source: String): Option[Int] =
      Parser(source).countRootArray()

    private final class Parser(source: String) {
      def countRootArray(): Option[Int] = {
        var index = skipWhitespace(0)
        if !hasChar(index, '[') then return None
        index = skipWhitespace(index + 1)
        if hasChar(index, ']') then return finish(0, index + 1)

        var count = 0
        while index < source.length do {
          parseValue(index) match {
            case None => return None
            case Some(nextIndex) =>
              count += 1
              index = skipWhitespace(nextIndex)
              if hasChar(index, ']') then return finish(count, index + 1)
              if !hasChar(index, ',') then return None
              index = skipWhitespace(index + 1)
          }
        }

        None
      }

      private def finish(count: Int, fromIndex: Int): Option[Int] =
        Option.when(skipWhitespace(fromIndex) >= source.length)(count)

      private def parseValue(start: Int): Option[Int] = {
        val index = skipWhitespace(start)
        if index >= source.length then None
        else
          source.charAt(index) match {
            case '"' => parseString(index)
            case '{' => parseObject(index)
            case '[' => parseArray(index)
            case 't' => parseLiteral(index, "true")
            case 'f' => parseLiteral(index, "false")
            case 'n' => parseLiteral(index, "null")
            case '-' => parseNumber(index)
            case char if char.isDigit => parseNumber(index)
            case _ => None
          }
      }

      private def parseObject(start: Int): Option[Int] = {
        var index = skipWhitespace(start + 1)
        if hasChar(index, '}') then return Some(index + 1)

        while index < source.length do {
          parseString(index) match {
            case None => return None
            case Some(afterKey) =>
              index = skipWhitespace(afterKey)
              if !hasChar(index, ':') then return None
              parseValue(index + 1) match {
                case None => return None
                case Some(afterValue) =>
                  index = skipWhitespace(afterValue)
                  if hasChar(index, '}') then return Some(index + 1)
                  if !hasChar(index, ',') then return None
                  index = skipWhitespace(index + 1)
              }
          }
        }

        None
      }

      private def parseArray(start: Int): Option[Int] = {
        var index = skipWhitespace(start + 1)
        if hasChar(index, ']') then return Some(index + 1)

        while index < source.length do {
          parseValue(index) match {
            case None => return None
            case Some(afterValue) =>
              index = skipWhitespace(afterValue)
              if hasChar(index, ']') then return Some(index + 1)
              if !hasChar(index, ',') then return None
              index = skipWhitespace(index + 1)
          }
        }

        None
      }

      private def parseString(start: Int): Option[Int] = {
        if !hasChar(start, '"') then return None
        var index = start + 1
        while index < source.length do {
          source.charAt(index) match {
            case '"' =>
              return Some(index + 1)
            case '\\' =>
              if index + 1 >= source.length then return None
              val escaped = source.charAt(index + 1)
              if escaped == 'u' then {
                val hexStart = index + 2
                val hexEnd = hexStart + 4
                if hexEnd > source.length || !source.substring(hexStart, hexEnd).forall(isHexDigit) then return None
                index = hexEnd
              } else if isSimpleEscape(escaped) then {
                index += 2
              } else return None
            case char if Character.isISOControl(char) =>
              return None
            case _ =>
              index += 1
          }
        }
        None
      }

      private def parseNumber(start: Int): Option[Int] = {
        var index = start
        if hasChar(index, '-') then index += 1

        if hasChar(index, '0') then index += 1
        else {
          val integerStart = index
          while index < source.length && source.charAt(index).isDigit do index += 1
          if index == integerStart then return None
        }

        if hasChar(index, '.') then {
          index += 1
          val fractionStart = index
          while index < source.length && source.charAt(index).isDigit do index += 1
          if index == fractionStart then return None
        }

        if hasChar(index, 'e') || hasChar(index, 'E') then {
          index += 1
          if hasChar(index, '+') || hasChar(index, '-') then index += 1
          val exponentStart = index
          while index < source.length && source.charAt(index).isDigit do index += 1
          if index == exponentStart then return None
        }

        Some(index)
      }

      private def parseLiteral(start: Int, expected: String): Option[Int] =
        Option.when(source.regionMatches(start, expected, 0, expected.length))(start + expected.length)

      private def skipWhitespace(start: Int): Int = {
        var index = start
        while index < source.length && source.charAt(index).isWhitespace do index += 1
        index
      }

      private def hasChar(index: Int, expected: Char): Boolean =
        index >= 0 && index < source.length && source.charAt(index) == expected

      private def isSimpleEscape(char: Char): Boolean =
        char == '"' || char == '\\' || char == '/' || char == 'b' || char == 'f' || char == 'n' || char == 'r' || char == 't'

      private def isHexDigit(char: Char): Boolean =
        (char >= '0' && char <= '9') ||
          (char >= 'a' && char <= 'f') ||
          (char >= 'A' && char <= 'F')
    }

    private object Parser {
      def apply(source: String): Parser =
        new Parser(source)
    }
  }
}
