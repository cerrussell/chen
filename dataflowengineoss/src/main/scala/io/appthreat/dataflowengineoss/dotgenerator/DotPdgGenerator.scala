package io.appthreat.dataflowengineoss.dotgenerator

import io.appthreat.dataflowengineoss.DefaultSemantics
import io.appthreat.dataflowengineoss.semanticsloader.Semantics
import io.shiftleft.codepropertygraph.generated.nodes.Method
import io.shiftleft.semanticcpg.dotgenerator.{CdgGenerator, DotSerializer}

object DotPdgGenerator {

  def toDotPdg(traversal: Iterator[Method])(implicit semantics: Semantics = DefaultSemantics()): Iterator[String] =
    traversal.map(dotGraphForMethod)

  private def dotGraphForMethod(method: Method)(implicit semantics: Semantics): String = {
    val ddg = new DdgGenerator().generate(method)
    val cdg = new CdgGenerator().generate(method)
    DotSerializer.dotGraph(Option(method), ddg.++(cdg), withEdgeTypes = true)
  }

}
