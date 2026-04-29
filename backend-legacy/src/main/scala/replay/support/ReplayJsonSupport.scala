package slaydemo.backend.replay.support

import java.io.InputStream
import java.nio.charset.StandardCharsets
import scala.annotation.tailrec

object ReplayJsonSupport {
  def parseFlatObject(input: InputStream): Either[String, Map[String, String]] = {
    val body = new String(input.readAllBytes(), StandardCharsets.UTF_8)
    parseFlatObject(body)
  }

  def parseFlatObject(body: String): Either[String, Map[String, String]] = {
    val trimmed = Option(body).getOrElse("").trim
    if (trimmed.isEmpty) {
      Right(Map.empty)
    } else {
      new Parser(trimmed).parseObjectFields()
    }
  }

  def validateArrayString(value: String): Either[String, Unit] = {
    val trimmed = Option(value).map(_.trim).getOrElse("")
    if (trimmed.isEmpty || trimmed == "null") {
      Right(())
    } else {
      new Parser(trimmed).parseArrayRoot().left.map(_ => "invalid_frames_json")
    }
  }

  def countArrayElements(value: String): Either[String, Int] = {
    val trimmed = Option(value).map(_.trim).getOrElse("")
    if (trimmed.isEmpty || trimmed == "null") {
      Right(0)
    } else {
      new Parser(trimmed).parseArrayRootCount().left.map(_ => "invalid_frames_json")
    }
  }

  private final class Parser(source: String) {
    private final case class Parsed[A](value: A, nextIndex: Int)

    def parseObjectFields(): Either[String, Map[String, String]] = {
      val start = skipWhitespace(0)
      if (!hasChar(start, '{')) {
        Left("Request body must be a JSON object.")
      } else {
        val fieldsStart = skipWhitespace(start + 1)
        if (hasChar(fieldsStart, '}')) {
          finish(Map.empty[String, String], fieldsStart + 1)
        } else {
          parseObjectEntries(fieldsStart, Map.empty).flatMap(parsed => finish(parsed.value, parsed.nextIndex))
        }
      }
    }

    def parseArrayRoot(): Either[String, Unit] = {
      parseArrayValue(skipWhitespace(0)).flatMap(parsed => finish((), parsed.nextIndex))
    }

    def parseArrayRootCount(): Either[String, Int] = {
      parseArrayValueCount(skipWhitespace(0)).flatMap(parsed => finish(parsed.value, parsed.nextIndex))
    }

    private def finish[A](result: A, fromIndex: Int): Either[String, A] =
      if (skipWhitespace(fromIndex) >= source.length) Right(result) else Left("Malformed JSON request body.")

    private def parseObjectEntries(
      fromIndex: Int,
      fields: Map[String, String]
    ): Either[String, Parsed[Map[String, String]]] =
      parseString(skipWhitespace(fromIndex)).flatMap { keyParsed =>
        val separatorIndex = skipWhitespace(keyParsed.nextIndex)
        if (!hasChar(separatorIndex, ':')) {
          Left("Malformed JSON request body.")
        } else {
          parseFieldValue(skipWhitespace(separatorIndex + 1)).flatMap { valueParsed =>
            val nextFields = valueParsed.value.fold(fields)(value => fields.updated(keyParsed.value, value))
            val delimiterIndex = skipWhitespace(valueParsed.nextIndex)
            if (hasChar(delimiterIndex, '}')) {
              Right(Parsed(nextFields, delimiterIndex + 1))
            } else if (hasChar(delimiterIndex, ',')) {
              parseObjectEntries(delimiterIndex + 1, nextFields)
            } else {
              Left("Malformed JSON request body.")
            }
          }
        }
      }

    private def parseFieldValue(fromIndex: Int): Either[String, Parsed[Option[String]]] = {
      if (fromIndex >= source.length) {
        Left("Malformed JSON request body.")
      } else {
        source.charAt(fromIndex) match {
          case '"' =>
            parseString(fromIndex).map(parsed => Parsed(Some(parsed.value), parsed.nextIndex))
          case '{' =>
            parseObjectValue(fromIndex).map(parsed => Parsed(None, parsed.nextIndex))
          case '[' =>
            parseArrayValue(fromIndex).map(parsed => Parsed(None, parsed.nextIndex))
          case 't' =>
            parseLiteral(fromIndex, "true").map(nextIndex => Parsed(Some("true"), nextIndex))
          case 'f' =>
            parseLiteral(fromIndex, "false").map(nextIndex => Parsed(Some("false"), nextIndex))
          case 'n' =>
            parseLiteral(fromIndex, "null").map(nextIndex => Parsed(Some("null"), nextIndex))
          case ch if ch == '-' || ch.isDigit =>
            parseNumber(fromIndex).map(parsed => Parsed(Some(parsed.value), parsed.nextIndex))
          case _ =>
            Left("Malformed JSON request body.")
        }
      }
    }

    private def parseValue(fromIndex: Int): Either[String, Parsed[Unit]] = {
      if (fromIndex >= source.length) {
        Left("Malformed JSON request body.")
      } else {
        source.charAt(fromIndex) match {
          case '"' =>
            parseString(fromIndex).map(parsed => Parsed((), parsed.nextIndex))
          case '{' =>
            parseObjectValue(fromIndex)
          case '[' =>
            parseArrayValue(fromIndex)
          case 't' =>
            parseLiteral(fromIndex, "true").map(nextIndex => Parsed((), nextIndex))
          case 'f' =>
            parseLiteral(fromIndex, "false").map(nextIndex => Parsed((), nextIndex))
          case 'n' =>
            parseLiteral(fromIndex, "null").map(nextIndex => Parsed((), nextIndex))
          case ch if ch == '-' || ch.isDigit =>
            parseNumber(fromIndex).map(parsed => Parsed((), parsed.nextIndex))
          case _ =>
            Left("Malformed JSON request body.")
        }
      }
    }

    private def parseObjectValue(fromIndex: Int): Either[String, Parsed[Unit]] = {
      if (!hasChar(fromIndex, '{')) {
        Left("Malformed JSON request body.")
      } else {
        val fieldsStart = skipWhitespace(fromIndex + 1)
        if (hasChar(fieldsStart, '}')) {
          Right(Parsed((), fieldsStart + 1))
        } else {
          parseObjectValueEntries(fieldsStart)
        }
      }
    }

    private def parseObjectValueEntries(fromIndex: Int): Either[String, Parsed[Unit]] =
      parseString(skipWhitespace(fromIndex)).flatMap { keyParsed =>
        val separatorIndex = skipWhitespace(keyParsed.nextIndex)
        if (!hasChar(separatorIndex, ':')) {
          Left("Malformed JSON request body.")
        } else {
          parseValue(skipWhitespace(separatorIndex + 1)).flatMap { valueParsed =>
            val delimiterIndex = skipWhitespace(valueParsed.nextIndex)
            if (hasChar(delimiterIndex, '}')) {
              Right(Parsed((), delimiterIndex + 1))
            } else if (hasChar(delimiterIndex, ',')) {
              parseObjectValueEntries(delimiterIndex + 1)
            } else {
              Left("Malformed JSON request body.")
            }
          }
        }
      }

    private def parseArrayValue(fromIndex: Int): Either[String, Parsed[Unit]] = {
      if (!hasChar(fromIndex, '[')) {
        Left("invalid_frames_json")
      } else {
        val valuesStart = skipWhitespace(fromIndex + 1)
        if (hasChar(valuesStart, ']')) {
          Right(Parsed((), valuesStart + 1))
        } else {
          parseArrayEntries(valuesStart)
        }
      }
    }

    private def parseArrayEntries(fromIndex: Int): Either[String, Parsed[Unit]] =
      parseValue(skipWhitespace(fromIndex)).flatMap { valueParsed =>
        val delimiterIndex = skipWhitespace(valueParsed.nextIndex)
        if (hasChar(delimiterIndex, ']')) {
          Right(Parsed((), delimiterIndex + 1))
        } else if (hasChar(delimiterIndex, ',')) {
          parseArrayEntries(delimiterIndex + 1)
        } else {
          Left("Malformed JSON request body.")
        }
      }

    private def parseArrayValueCount(fromIndex: Int): Either[String, Parsed[Int]] = {
      if (!hasChar(fromIndex, '[')) {
        Left("invalid_frames_json")
      } else {
        val valuesStart = skipWhitespace(fromIndex + 1)
        if (hasChar(valuesStart, ']')) {
          Right(Parsed(0, valuesStart + 1))
        } else {
          parseArrayCountEntries(valuesStart, 0)
        }
      }
    }

    private def parseArrayCountEntries(fromIndex: Int, count: Int): Either[String, Parsed[Int]] =
      parseValue(skipWhitespace(fromIndex)).flatMap { valueParsed =>
        val nextCount = count + 1
        val delimiterIndex = skipWhitespace(valueParsed.nextIndex)
        if (hasChar(delimiterIndex, ']')) {
          Right(Parsed(nextCount, delimiterIndex + 1))
        } else if (hasChar(delimiterIndex, ',')) {
          parseArrayCountEntries(delimiterIndex + 1, nextCount)
        } else {
          Left("Malformed JSON request body.")
        }
      }

    private def parseString(fromIndex: Int): Either[String, Parsed[String]] = {
      if (!hasChar(fromIndex, '"')) {
        Left("Malformed JSON request body.")
      } else {
        parseStringChars(fromIndex + 1, new StringBuilder)
      }
    }

    private def parseStringChars(fromIndex: Int, builder: StringBuilder): Either[String, Parsed[String]] = {
      if (fromIndex >= source.length) {
        Left("Malformed JSON request body.")
      } else {
        val ch = source.charAt(fromIndex)
        ch match {
          case '"' =>
            Right(Parsed(builder.result(), fromIndex + 1))
          case '\\' =>
            if (fromIndex + 1 >= source.length) {
              Left("Malformed JSON request body.")
            } else {
              parseEscapedStringChar(fromIndex + 1, builder).flatMap { nextIndex =>
                parseStringChars(nextIndex, builder)
              }
            }
          case value if Character.isISOControl(value) =>
            Left("Malformed JSON request body.")
          case value =>
            builder += value
            parseStringChars(fromIndex + 1, builder)
        }
      }
    }

    private def parseEscapedStringChar(fromIndex: Int, builder: StringBuilder): Either[String, Int] =
      source.charAt(fromIndex) match {
        case '"' =>
          builder += '"'
          Right(fromIndex + 1)
        case '\\' =>
          builder += '\\'
          Right(fromIndex + 1)
        case '/' =>
          builder += '/'
          Right(fromIndex + 1)
        case 'b' =>
          builder += '\b'
          Right(fromIndex + 1)
        case 'f' =>
          builder += '\f'
          Right(fromIndex + 1)
        case 'n' =>
          builder += '\n'
          Right(fromIndex + 1)
        case 'r' =>
          builder += '\r'
          Right(fromIndex + 1)
        case 't' =>
          builder += '\t'
          Right(fromIndex + 1)
        case 'u' =>
          if (fromIndex + 4 >= source.length) {
            Left("Malformed JSON request body.")
          } else {
            val hex = source.substring(fromIndex + 1, fromIndex + 5)
            if (hex.forall(isHexDigit)) {
              builder ++= Character.toString(Integer.parseInt(hex, 16).toChar)
              Right(fromIndex + 5)
            } else {
              Left("Malformed JSON request body.")
            }
          }
        case _ =>
          Left("Malformed JSON request body.")
      }

    private def parseLiteral(fromIndex: Int, expected: String): Either[String, Int] = {
      if (source.regionMatches(fromIndex, expected, 0, expected.length)) {
        Right(fromIndex + expected.length)
      } else {
        Left("Malformed JSON request body.")
      }
    }

    private def parseNumber(fromIndex: Int): Either[String, Parsed[String]] = {
      val afterSign =
        if (hasChar(fromIndex, '-')) fromIndex + 1 else fromIndex

      if (afterSign >= source.length) {
        Left("Malformed JSON request body.")
      } else {
        val afterInteger =
          if (hasChar(afterSign, '0')) {
            Right(afterSign + 1)
          } else if (source.charAt(afterSign).isDigit) {
            Right(readDigits(afterSign))
          } else {
            Left("Malformed JSON request body.")
          }

        afterInteger
          .flatMap(parseFraction)
          .flatMap(parseExponent)
          .map(nextIndex => Parsed(source.substring(fromIndex, nextIndex), nextIndex))
      }
    }

    private def parseFraction(fromIndex: Int): Either[String, Int] =
      if (!hasChar(fromIndex, '.')) {
        Right(fromIndex)
      } else {
        val digitStart = fromIndex + 1
        if (digitStart >= source.length || !source.charAt(digitStart).isDigit) {
          Left("Malformed JSON request body.")
        } else {
          Right(readDigits(digitStart))
        }
      }

    private def parseExponent(fromIndex: Int): Either[String, Int] =
      if (!hasChar(fromIndex, 'e') && !hasChar(fromIndex, 'E')) {
        Right(fromIndex)
      } else {
        val exponentStart = fromIndex + 1
        val digitStart =
          if (hasChar(exponentStart, '+') || hasChar(exponentStart, '-')) exponentStart + 1 else exponentStart
        if (digitStart >= source.length || !source.charAt(digitStart).isDigit) {
          Left("Malformed JSON request body.")
        } else {
          Right(readDigits(digitStart))
        }
      }

    @tailrec
    private def skipWhitespace(fromIndex: Int): Int =
      if (fromIndex < source.length && source.charAt(fromIndex).isWhitespace) {
        skipWhitespace(fromIndex + 1)
      } else {
        fromIndex
      }

    @tailrec
    private def readDigits(fromIndex: Int): Int =
      if (fromIndex < source.length && source.charAt(fromIndex).isDigit) {
        readDigits(fromIndex + 1)
      } else {
        fromIndex
      }

    private def hasChar(fromIndex: Int, expected: Char): Boolean =
      fromIndex < source.length && source.charAt(fromIndex) == expected

    private def isHexDigit(value: Char): Boolean = {
      (value >= '0' && value <= '9') ||
      (value >= 'a' && value <= 'f') ||
      (value >= 'A' && value <= 'F')
    }
  }
}
