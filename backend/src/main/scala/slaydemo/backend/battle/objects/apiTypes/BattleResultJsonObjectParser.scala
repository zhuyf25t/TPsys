package slaydemo.backend.battle.objects.apiTypes

private[apiTypes] enum ResultJsonValue {
  case StringValue(value: String)
  case NumberValue(value: Double)
  case BooleanValue(value: Boolean)
  case NullValue
}

private[apiTypes] enum ResultJsonParseError {
  case ExpectedObject
  case ExpectedField
  case ExpectedValue
}

private[apiTypes] object ResultJsonObjectParser {
  def parse(body: String): Either[ResultJsonParseError, Map[String, ResultJsonValue]] = {
    val trimmed = Option(body).getOrElse("").trim
    if trimmed.isEmpty then Right(Map.empty)
    else Parser(trimmed).parse()
  }

  private final class Parser(source: String) {
    def parse(): Either[ResultJsonParseError, Map[String, ResultJsonValue]] = {
      var index = skipWhitespace(0)
      if !hasChar(index, '{') then return Left(ResultJsonParseError.ExpectedObject)
      index = skipWhitespace(index + 1)

      var fields = Map.empty[String, ResultJsonValue]
      if hasChar(index, '}') then return Right(fields)

      while index < source.length do {
        parseString(index) match {
          case None =>
            return Left(ResultJsonParseError.ExpectedField)
          case Some((key, afterKey)) =>
            index = skipWhitespace(afterKey)
            if !hasChar(index, ':') then return Left(ResultJsonParseError.ExpectedField)
            index = skipWhitespace(index + 1)

            parseValue(index) match {
              case None =>
                return Left(ResultJsonParseError.ExpectedValue)
              case Some((value, afterValue)) =>
                fields = fields.updated(key, value)
                index = skipWhitespace(afterValue)
                if hasChar(index, '}') then return Right(fields)
                if !hasChar(index, ',') then return Left(ResultJsonParseError.ExpectedField)
                index = skipWhitespace(index + 1)
            }
        }
      }

      Left(ResultJsonParseError.ExpectedObject)
    }

    private def parseValue(start: Int): Option[(ResultJsonValue, Int)] =
      if hasChar(start, '"') then
        parseString(start).map { case (value, next) => ResultJsonValue.StringValue(value) -> next }
      else if startsWith(start, "null") then
        Some(ResultJsonValue.NullValue -> (start + 4))
      else if startsWith(start, "true") then
        Some(ResultJsonValue.BooleanValue(true) -> (start + 4))
      else if startsWith(start, "false") then
        Some(ResultJsonValue.BooleanValue(false) -> (start + 5))
      else parseNumber(start)

    private def parseNumber(start: Int): Option[(ResultJsonValue, Int)] = {
      var index = start
      if hasChar(index, '-') then index += 1
      val digitsStart = index
      while index < source.length && source.charAt(index).isDigit do index += 1

      if index == digitsStart then None
      else
        if hasChar(index, '.') then {
          index += 1
          val fractionStart = index
          while index < source.length && source.charAt(index).isDigit do index += 1
          if index == fractionStart then return None
        }
        val text = source.substring(start, index)
        text.toDoubleOption
          .filter(value => java.lang.Double.isFinite(value))
          .map(value => ResultJsonValue.NumberValue(value) -> index)
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
            case None =>
              return None
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

    private def startsWith(index: Int, expected: String): Boolean =
      source.regionMatches(index, expected, 0, expected.length)

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
