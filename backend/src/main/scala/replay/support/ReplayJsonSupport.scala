package slaydemo.backend.replay.support

import java.io.InputStream
import java.nio.charset.StandardCharsets
import scala.collection.mutable

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
    private var index = 0

    def parseObjectFields(): Either[String, Map[String, String]] = {
      skipWhitespace()
      if (!consume('{')) {
        Left("Request body must be a JSON object.")
      } else {
        val fields = mutable.LinkedHashMap.empty[String, String]
        skipWhitespace()
        if (consume('}')) {
          finish(fields.toMap)
        } else {
          parseObjectEntries(fields).flatMap(_ => finish(fields.toMap))
        }
      }
    }

    def parseArrayRoot(): Either[String, Unit] = {
      skipWhitespace()
      parseArrayValue().flatMap(_ => finish(()))
    }

    def parseArrayRootCount(): Either[String, Int] = {
      skipWhitespace()
      parseArrayValueCount().flatMap(count => finish(count))
    }

    private def finish[A](result: A): Either[String, A] = {
      skipWhitespace()
      if (atEnd) Right(result) else Left("Malformed JSON request body.")
    }

    private def parseObjectEntries(fields: mutable.LinkedHashMap[String, String]): Either[String, Unit] = {
      var done = false
      while (!done) {
        skipWhitespace()
        parseString() match {
          case Left(error) => return Left(error)
          case Right(key) =>
            skipWhitespace()
            if (!consume(':')) {
              return Left("Malformed JSON request body.")
            }

            skipWhitespace()
            parseFieldValue() match {
              case Left(error) => return Left(error)
              case Right(Some(value)) =>
                fields.update(key, value)
              case Right(None) =>
            }

            skipWhitespace()
            if (consume('}')) {
              done = true
            } else if (!consume(',')) {
              return Left("Malformed JSON request body.")
            }
        }
      }

      Right(())
    }

    private def parseFieldValue(): Either[String, Option[String]] = {
      if (atEnd) {
        Left("Malformed JSON request body.")
      } else {
        currentChar match {
          case '"' =>
            parseString().map(Some(_))
          case '{' =>
            parseObjectValue().map(_ => None)
          case '[' =>
            parseArrayValue().map(_ => None)
          case 't' =>
            parseLiteral("true").map(_ => Some("true"))
          case 'f' =>
            parseLiteral("false").map(_ => Some("false"))
          case 'n' =>
            parseLiteral("null").map(_ => Some("null"))
          case ch if ch == '-' || ch.isDigit =>
            parseNumber().map(Some(_))
          case _ =>
            Left("Malformed JSON request body.")
        }
      }
    }

    private def parseValue(): Either[String, Unit] = {
      if (atEnd) {
        Left("Malformed JSON request body.")
      } else {
        currentChar match {
          case '"' =>
            parseString().map(_ => ())
          case '{' =>
            parseObjectValue()
          case '[' =>
            parseArrayValue()
          case 't' =>
            parseLiteral("true")
          case 'f' =>
            parseLiteral("false")
          case 'n' =>
            parseLiteral("null")
          case ch if ch == '-' || ch.isDigit =>
            parseNumber().map(_ => ())
          case _ =>
            Left("Malformed JSON request body.")
        }
      }
    }

    private def parseObjectValue(): Either[String, Unit] = {
      if (!consume('{')) {
        return Left("Malformed JSON request body.")
      }

      skipWhitespace()
      if (consume('}')) {
        return Right(())
      }

      var done = false
      while (!done) {
        skipWhitespace()
        parseString() match {
          case Left(error) => return Left(error)
          case Right(_) =>
            skipWhitespace()
            if (!consume(':')) {
              return Left("Malformed JSON request body.")
            }

            skipWhitespace()
            parseValue() match {
              case Left(error) => return Left(error)
              case Right(_) =>
            }

            skipWhitespace()
            if (consume('}')) {
              done = true
            } else if (!consume(',')) {
              return Left("Malformed JSON request body.")
            }
        }
      }

      Right(())
    }

    private def parseArrayValue(): Either[String, Unit] = {
      if (!consume('[')) {
        return Left("invalid_frames_json")
      }

      skipWhitespace()
      if (consume(']')) {
        return Right(())
      }

      var done = false
      while (!done) {
        skipWhitespace()
        parseValue() match {
          case Left(error) => return Left(error)
          case Right(_) =>
            skipWhitespace()
            if (consume(']')) {
              done = true
            } else if (!consume(',')) {
              return Left("Malformed JSON request body.")
            }
        }
      }

      Right(())
    }

    private def parseArrayValueCount(): Either[String, Int] = {
      if (!consume('[')) {
        return Left("invalid_frames_json")
      }

      skipWhitespace()
      if (consume(']')) {
        return Right(0)
      }

      var count = 0
      var done = false
      while (!done) {
        skipWhitespace()
        parseValue() match {
          case Left(error) => return Left(error)
          case Right(_) =>
            count += 1
            skipWhitespace()
            if (consume(']')) {
              done = true
            } else if (!consume(',')) {
              return Left("Malformed JSON request body.")
            }
        }
      }

      Right(count)
    }

    private def parseString(): Either[String, String] = {
      if (!consume('"')) {
        return Left("Malformed JSON request body.")
      }

      val builder = new StringBuilder
      while (!atEnd) {
        val ch = currentChar
        index += 1
        ch match {
          case '"' =>
            return Right(builder.result())
          case '\\' =>
            if (atEnd) {
              return Left("Malformed JSON request body.")
            }

            val escaped = currentChar
            index += 1
            escaped match {
              case '"'  => builder += '"'
              case '\\' => builder += '\\'
              case '/'  => builder += '/'
              case 'b'  => builder += '\b'
              case 'f'  => builder += '\f'
              case 'n'  => builder += '\n'
              case 'r'  => builder += '\r'
              case 't'  => builder += '\t'
              case 'u' =>
                decodeUnicodeEscape() match {
                  case Left(error)  => return Left(error)
                  case Right(value) => builder ++= value
                }
              case _ =>
                return Left("Malformed JSON request body.")
            }
          case value if Character.isISOControl(value) =>
            return Left("Malformed JSON request body.")
          case value =>
            builder += value
        }
      }

      Left("Malformed JSON request body.")
    }

    private def decodeUnicodeEscape(): Either[String, String] = {
      if (index + 4 > source.length) {
        Left("Malformed JSON request body.")
      } else {
        val hex = source.substring(index, index + 4)
        if (hex.forall(isHexDigit)) {
          index += 4
          Right(Character.toString(Integer.parseInt(hex, 16).toChar))
        } else {
          Left("Malformed JSON request body.")
        }
      }
    }

    private def parseLiteral(expected: String): Either[String, Unit] = {
      if (source.regionMatches(index, expected, 0, expected.length)) {
        index += expected.length
        Right(())
      } else {
        Left("Malformed JSON request body.")
      }
    }

    private def parseNumber(): Either[String, String] = {
      val start = index

      if (consume('-') && atEnd) {
        return Left("Malformed JSON request body.")
      }

      if (atEnd) {
        return Left("Malformed JSON request body.")
      }

      if (currentChar == '0') {
        index += 1
      } else if (currentChar.isDigit) {
        while (!atEnd && currentChar.isDigit) {
          index += 1
        }
      } else {
        return Left("Malformed JSON request body.")
      }

      if (!atEnd && currentChar == '.') {
        index += 1
        if (atEnd || !currentChar.isDigit) {
          return Left("Malformed JSON request body.")
        }
        while (!atEnd && currentChar.isDigit) {
          index += 1
        }
      }

      if (!atEnd && (currentChar == 'e' || currentChar == 'E')) {
        index += 1
        if (!atEnd && (currentChar == '+' || currentChar == '-')) {
          index += 1
        }
        if (atEnd || !currentChar.isDigit) {
          return Left("Malformed JSON request body.")
        }
        while (!atEnd && currentChar.isDigit) {
          index += 1
        }
      }

      Right(source.substring(start, index))
    }

    private def skipWhitespace(): Unit = {
      while (!atEnd && currentChar.isWhitespace) {
        index += 1
      }
    }

    private def consume(expected: Char): Boolean = {
      if (!atEnd && currentChar == expected) {
        index += 1
        true
      } else {
        false
      }
    }

    private def currentChar: Char = source.charAt(index)

    private def atEnd: Boolean = index >= source.length

    private def isHexDigit(value: Char): Boolean = {
      (value >= '0' && value <= '9') ||
      (value >= 'a' && value <= 'f') ||
      (value >= 'A' && value <= 'F')
    }
  }
}
