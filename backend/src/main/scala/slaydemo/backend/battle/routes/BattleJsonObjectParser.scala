package slaydemo.backend.battle.routes

private[routes] enum BattleJsonValue {
  case StringValue(value: String)
  case NumberValue(value: Double)
  case BooleanValue(value: Boolean)
  case ObjectValue(fields: Map[String, BattleJsonValue])
  case NullValue
}

private[routes] enum BattleJsonParseError {
  case ExpectedObject
  case ExpectedField
  case ExpectedValue
}

private[routes] object BattleJsonObjectParser {
  def parse(body: String): Either[BattleJsonParseError, Map[String, BattleJsonValue]] = {
    val trimmed = Option(body).getOrElse("").trim
    if trimmed.isEmpty then Right(Map.empty)
    else Parser(trimmed).parse()
  }

  private final class Parser(source: String) {
    def parse(): Either[BattleJsonParseError, Map[String, BattleJsonValue]] = {
      parseObject(0) match {
        case Some((fields, nextIndex)) if skipWhitespace(nextIndex) == source.length =>
          Right(fields)
        case _ =>
          Left(BattleJsonParseError.ExpectedObject)
      }
    }

    private def parseValue(start: Int): Option[(BattleJsonValue, Int)] =
      if hasChar(start, '"') then
        parseString(start).map { case (value, next) => BattleJsonValue.StringValue(value) -> next }
      else if hasChar(start, '{') then
        parseObject(start).map { case (fields, next) => BattleJsonValue.ObjectValue(fields) -> next }
      else if startsWith(start, "null") then
        Some(BattleJsonValue.NullValue -> (start + 4))
      else if startsWith(start, "true") then
        Some(BattleJsonValue.BooleanValue(true) -> (start + 4))
      else if startsWith(start, "false") then
        Some(BattleJsonValue.BooleanValue(false) -> (start + 5))
      else parseNumber(start)

    private def parseObject(start: Int): Option[(Map[String, BattleJsonValue], Int)] = {
      var index = skipWhitespace(start)
      if !hasChar(index, '{') then return None
      index = skipWhitespace(index + 1)

      var fields = Map.empty[String, BattleJsonValue]
      if hasChar(index, '}') then return Some(fields -> (index + 1))

      while index < source.length do {
        parseString(index) match {
          case None =>
            return None
          case Some((key, afterKey)) =>
            index = skipWhitespace(afterKey)
            if !hasChar(index, ':') then return None
            index = skipWhitespace(index + 1)

            parseValue(index) match {
              case None =>
                return None
              case Some((value, afterValue)) =>
                fields = fields.updated(key, value)
                index = skipWhitespace(afterValue)
                if hasChar(index, '}') then return Some(fields -> (index + 1))
                if !hasChar(index, ',') then return None
                index = skipWhitespace(index + 1)
            }
        }
      }

      None
    }

    private def parseNumber(start: Int): Option[(BattleJsonValue, Int)] = {
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
        if hasChar(index, 'e') || hasChar(index, 'E') then {
          index += 1
          if hasChar(index, '+') || hasChar(index, '-') then index += 1
          val exponentStart = index
          while index < source.length && source.charAt(index).isDigit do index += 1
          if index == exponentStart then return None
        }

        val text = source.substring(start, index)
        text.toDoubleOption
          .filter(value => java.lang.Double.isFinite(value))
          .map(value => BattleJsonValue.NumberValue(value) -> index)
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
