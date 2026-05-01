package slaydemo.backend.shared.json

enum JsonObjectParseError {
  case ExpectedObject
  case ExpectedStringField
}

object JsonObjectParser {
  def parseStringFields(body: String): Either[JsonObjectParseError, Map[String, String]] = {
    val trimmed = Option(body).getOrElse("").trim
    if trimmed.isEmpty then Right(Map.empty)
    else Parser(trimmed).parse()
  }

  private final class Parser(source: String) {
    def parse(): Either[JsonObjectParseError, Map[String, String]] = {
      var index = skipWhitespace(0)
      if !hasChar(index, '{') then return Left(JsonObjectParseError.ExpectedObject)
      index = skipWhitespace(index + 1)

      var fields = Map.empty[String, String]
      if hasChar(index, '}') then return Right(fields)

      while index < source.length do {
        parseString(index) match {
          case None => return Left(JsonObjectParseError.ExpectedStringField)
          case Some((key, afterKey)) =>
            index = skipWhitespace(afterKey)
            if !hasChar(index, ':') then return Left(JsonObjectParseError.ExpectedStringField)
            index = skipWhitespace(index + 1)

            parseString(index) match {
              case None => return Left(JsonObjectParseError.ExpectedStringField)
              case Some((value, afterValue)) =>
                fields = fields.updated(key, value)
                index = skipWhitespace(afterValue)
                if hasChar(index, '}') then return Right(fields)
                if !hasChar(index, ',') then return Left(JsonObjectParseError.ExpectedStringField)
                index = skipWhitespace(index + 1)
            }
        }
      }

      Left(JsonObjectParseError.ExpectedObject)
    }

    private def parseString(start: Int): Option[(String, Int)] = {
      if !hasChar(start, '"') then return None

      val builder = StringBuilder()
      var index = start + 1
      var escaped = false

      while index < source.length do {
        val char = source.charAt(index)
        if escaped then {
          decodeEscaped(char, index) match {
            case None => return None
            case Some((decoded, nextIndex)) =>
              builder.append(decoded)
              index = nextIndex
              escaped = false
          }
        } else if char == '\\' then {
          escaped = true
          index += 1
        } else if char == '"' then {
          return Some(builder.result() -> (index + 1))
        } else {
          builder.append(char)
          index += 1
        }
      }

      None
    }

    private def decodeEscaped(char: Char, index: Int): Option[(Char, Int)] =
      char match {
        case '"'  => Some('"' -> (index + 1))
        case '\\' => Some('\\' -> (index + 1))
        case '/'  => Some('/' -> (index + 1))
        case 'b'  => Some('\b' -> (index + 1))
        case 'f'  => Some('\f' -> (index + 1))
        case 'n'  => Some('\n' -> (index + 1))
        case 'r'  => Some('\r' -> (index + 1))
        case 't'  => Some('\t' -> (index + 1))
        case 'u'  => decodeUnicodeEscape(index + 1)
        case _    => None
      }

    private def decodeUnicodeEscape(start: Int): Option[(Char, Int)] = {
      val end = start + 4
      if end > source.length then return None

      val hex = source.substring(start, end)
      if !hex.forall(isHexDigit) then None
      else Some(Integer.parseInt(hex, 16).toChar -> end)
    }

    private def skipWhitespace(start: Int): Int = {
      var index = start
      while index < source.length && source.charAt(index).isWhitespace do index += 1
      index
    }

    private def hasChar(index: Int, expected: Char): Boolean =
      index >= 0 && index < source.length && source.charAt(index) == expected

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
