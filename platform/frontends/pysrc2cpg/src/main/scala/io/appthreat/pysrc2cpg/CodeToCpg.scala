package io.appthreat.pysrc2cpg

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.ConcurrentWriterCpgPass
import Py2Cpg.InputProvider
import io.appthreat.pythonparser.PyParser
import io.appthreat.x2cpg.ValidationMode
import org.slf4j.LoggerFactory

class CodeToCpg(cpg: Cpg, inputProvider: Iterable[InputProvider], schemaValidationMode: ValidationMode)
    extends ConcurrentWriterCpgPass[InputProvider](cpg) {
  import CodeToCpg.logger

  override def generateParts(): Array[InputProvider] = inputProvider.toArray

  override def runOnPart(diffGraph: DiffGraphBuilder, inputProvider: InputProvider): Unit = {
    val inputPair = inputProvider()
    try {
      val parser                 = new PyParser()
      val lineBreakCorrectedCode = inputPair.content.replace("\r\n", "\n").replace("\r", "\n")
      val astRoot                = parser.parse(lineBreakCorrectedCode)
      val nodeToCode             = new NodeToCode(lineBreakCorrectedCode)
      val astVisitor = new PythonAstVisitor(inputPair.relFileName, nodeToCode, PythonV2AndV3)(schemaValidationMode)
      astVisitor.convert(astRoot)

      diffGraph.absorb(astVisitor.getDiffGraph)
    } catch {
      case exception: Throwable =>
        logger.warn(s"Failed to convert file ${inputPair.relFileName}", exception)
        Iterator.empty
    }
  }

}

object CodeToCpg {
  private val logger = LoggerFactory.getLogger(getClass)
}
