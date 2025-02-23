package io.appthreat.pythonparser

import io.appthreat.pythonparser.ast.{ErrorStatement, iast}
import io.appthreat.pythonparser.PythonParser
import io.appthreat.pythonparser.PythonParserConstants

import java.io.{BufferedReader, ByteArrayInputStream, InputStream, Reader}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

class PyParser {
  private var pythonParser: PythonParser = _

  def parse(code: String): iast = {
    parse(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)))
  }

  def parse(inputStream: InputStream): iast = {
    pythonParser = new PythonParser(new CharStreamImpl(inputStream))
    // We start in INDENT_CHECK lexer state because we want to detect indentations
    // also for the first line.
    pythonParser.token_source.SwitchTo(PythonParserConstants.INDENT_CHECK)
    val module = pythonParser.module()
    module
  }

  def errors: Iterable[ErrorStatement] = {
    pythonParser.getErrors.asScala
  }
}
