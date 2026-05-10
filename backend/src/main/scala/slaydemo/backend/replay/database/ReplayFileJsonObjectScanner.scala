package slaydemo.backend.replay.database

private[database] object ReplayFileJsonObjectScanner {
  def extractArrayObjects(raw: String, field: String): Vector[String] = {
    val marker = raw.indexOf(s""""$field"""")
    if marker < 0 then Vector.empty
    else {
      val start = raw.indexOf('[', marker)
      val end = findMatchingDelimiter(raw, start, '[', ']')
      if start < 0 || end < 0 || end <= start then Vector.empty
      else extractObjects(raw.substring(start + 1, end))
    }
  }

  private def extractObjects(section: String): Vector[String] = {
    var chunks = Vector.empty[String]
    var depth = 0
    var start = -1
    var index = 0
    var inString = false
    var escaped = false
    while index < section.length do {
      val char = section.charAt(index)
      if inString then {
        if escaped then escaped = false
        else if char == '\\' then escaped = true
        else if char == '"' then inString = false
      } else {
        char match {
          case '"' =>
            inString = true
          case '{' =>
            if depth == 0 then start = index + 1
            depth += 1
          case '}' =>
            depth -= 1
            if depth == 0 && start >= 0 then {
              chunks = chunks :+ section.substring(start, index)
              start = -1
            }
          case _ =>
        }
      }
      index += 1
    }
    chunks
  }

  private def findMatchingDelimiter(raw: String, start: Int, open: Char, close: Char): Int = {
    if start < 0 || start >= raw.length || raw.charAt(start) != open then -1
    else {
      var depth = 0
      var index = start
      var inString = false
      var escaped = false
      while index < raw.length do {
        val char = raw.charAt(index)
        if inString then {
          if escaped then escaped = false
          else if char == '\\' then escaped = true
          else if char == '"' then inString = false
        } else {
          char match {
            case '"' =>
              inString = true
            case value if value == open =>
              depth += 1
            case value if value == close =>
              depth -= 1
              if depth == 0 then return index
            case _ =>
          }
        }
        index += 1
      }
      -1
    }
  }
}
