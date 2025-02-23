package io.appthreat.javasrc2cpg.passes

import better.files.File
import com.github.javaparser.{JavaParser, ParserConfiguration}
import com.github.javaparser.ParserConfiguration.LanguageLevel
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node.Parsedness
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.{
  ClassLoaderTypeSolver,
  JarTypeSolver,
  ReflectionTypeSolver
}
import io.appthreat.javasrc2cpg.{Config, JavaSrc2Cpg, util}
import io.appthreat.javasrc2cpg.util.{Delombok, SourceParser}
import io.appthreat.javasrc2cpg.JavaSrc2Cpg.JavaSrcEnvVar
import io.appthreat.javasrc2cpg.typesolvers.noncaching.JdkJarTypeSolver
import io.appthreat.javasrc2cpg.typesolvers.{EagerSourceTypeSolver, SimpleCombinedTypeSolver}
import io.appthreat.javasrc2cpg.passes.AstCreationPass.*
import io.appthreat.javasrc2cpg.util.Delombok.DelombokMode
import io.appthreat.javasrc2cpg.util.Delombok.DelombokMode.*
import io.appthreat.x2cpg.SourceFiles
import io.appthreat.x2cpg.datastructures.Global
import io.appthreat.x2cpg.passes.frontend.XTypeRecoveryConfig
import io.appthreat.x2cpg.utils.dependency.DependencyResolver
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.ConcurrentWriterCpgPass
import org.slf4j.LoggerFactory

import java.net.URLClassLoader
import java.nio.file.{Path, Paths}
import scala.collection.mutable
import scala.collection.parallel.CollectionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.RichOptional
import scala.util.{Success, Try}

class AstCreationPass(config: Config, cpg: Cpg, sourcesOverride: Option[List[String]] = None)
    extends ConcurrentWriterCpgPass[String](cpg) {

  val global: Global = new Global()
  private val logger = LoggerFactory.getLogger(classOf[AstCreationPass])

  private val sourceFilenames = SourceParser.getSourceFilenames(config, sourcesOverride)

  val (sourceParser, symbolSolver, packagesJarMappings) = initParserAndUtils(config, sourceFilenames)

  override def generateParts(): Array[String] = sourceFilenames

  override def runOnPart(diffGraph: DiffGraphBuilder, filename: String): Unit = {
    val relativeFilename = Path.of(config.inputPath).relativize(Path.of(filename)).toString
    sourceParser.parseAnalysisFile(relativeFilename) match {
      case Some(compilationUnit) =>
        symbolSolver.inject(compilationUnit)
        diffGraph.absorb(
          new AstCreator(relativeFilename, compilationUnit, global, symbolSolver, packagesJarMappings)(
            config.schemaValidation
          ).createAst()
        )

      case None => logger.warn(s"Skipping AST creation for $filename")
    }
  }

  private def initParserAndUtils(
    config: Config,
    sourceFilenames: Array[String]
  ): (SourceParser, JavaSymbolSolver, mutable.Map[String, mutable.Set[String]]) = {
    val dependencies                        = getDependencyList(config.inputPath)
    val sourceParser                        = util.SourceParser(config, dependencies.exists(_.contains("lombok")))
    val (symbolSolver, packagesJarMappings) = createSymbolSolver(config, dependencies, sourceParser, sourceFilenames)
    (sourceParser, symbolSolver, packagesJarMappings)
  }

  private def getDependencyList(inputPath: String): List[String] = {
    if (config.fetchDependencies) {
      DependencyResolver.getDependencies(Paths.get(inputPath)) match {
        case Some(deps) => deps.toList
        case None =>
          logger.warn(s"Could not fetch dependencies for project at path $inputPath")
          List()
      }
    } else {
      logger.debug("dependency resolving disabled")
      List()
    }
  }

  private def createSymbolSolver(
    config: Config,
    dependencies: List[String],
    sourceParser: SourceParser,
    sourceFilenames: Array[String]
  ): (JavaSymbolSolver, mutable.Map[String, mutable.Set[String]]) = {
    val combinedTypeSolver = new SimpleCombinedTypeSolver()
    val symbolSolver       = new JavaSymbolSolver(combinedTypeSolver)

    val jdkPathFromEnvVar = Option(System.getenv(JavaSrcEnvVar.JdkPath.name))
    val jdkPath = (config.jdkPath, jdkPathFromEnvVar) match {
      case (None, None) =>
        val javaHome = System.getProperty("java.home")
        logger.debug(s"No explicit jdk-path set in , so using system java.home for JDK type information: $javaHome")
        javaHome

      case (None, Some(jdkPath)) =>
        logger.debug(
          s"Using JDK path from environment variable ${JavaSrcEnvVar.JdkPath.name} for JDK type information: $jdkPath"
        )
        jdkPath

      case (Some(jdkPath), _) =>
        logger.debug(s"Using JDK path set with jdk-path option for JDK type information: $jdkPath")
        jdkPath
    }

    val jdkJarTypeSolver = JdkJarTypeSolver.fromJdkPath(jdkPath)
    combinedTypeSolver.addNonCachingTypeSolver(jdkJarTypeSolver)

    val relativeSourceFilenames =
      sourceFilenames.map(filename => Path.of(config.inputPath).relativize(Path.of(filename)).toString)

    val sourceTypeSolver =
      EagerSourceTypeSolver(relativeSourceFilenames, sourceParser, combinedTypeSolver, symbolSolver)

    combinedTypeSolver.addCachingTypeSolver(sourceTypeSolver)

    // Add solvers for inference jars
    val jarsList = config.inferenceJarPaths.flatMap(recursiveJarsFromPath).toList
    (jarsList ++ dependencies)
      .flatMap { path =>
        Try(new JarTypeSolver(path)).toOption
      }
      .foreach { combinedTypeSolver.addNonCachingTypeSolver(_) }

    (symbolSolver, jdkJarTypeSolver.packagesJarMappings)
  }

  private def recursiveJarsFromPath(path: String): List[String] = {
    Try(File(path)) match {
      case Success(file) if file.isDirectory =>
        file.listRecursively
          .map(_.canonicalPath)
          .filter(_.endsWith(".jar"))
          .toList

      case Success(file) if file.canonicalPath.endsWith(".jar") =>
        List(file.canonicalPath)

      case _ =>
        Nil
    }
  }
}
