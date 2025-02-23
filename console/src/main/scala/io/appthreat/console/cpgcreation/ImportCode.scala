package io.appthreat.console.cpgcreation

import better.files.File
import io.appthreat.console.{Console, FrontendConfig, Reporting}
import io.appthreat.console.workspacehandling.Project
import io.appthreat.console.{ConsoleException, FrontendConfig, Reporting}
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Languages
import overflowdb.traversal.help.Table
import me.shadaj.scalapy.py
import me.shadaj.scalapy.py.SeqConverters
import py.PyQuote
import me.shadaj.scalapy.interpreter.CPythonInterpreter

import java.nio.file.Path
import scala.util.{Failure, Success, Try}

class ImportCode[T <: Project](console: Console[T]) extends Reporting {
  import Console._

  private val config             = console.config
  private val workspace          = console.workspace
  protected val generatorFactory = new CpgGeneratorFactory(config)
  val chenPyUtils                = py.module("chenpy.utils")

  private def checkInputPath(inputPath: String): Unit = {
    if (!File(inputPath).exists) {
      throw new ConsoleException(s"Input path does not exist: '$inputPath'")
    }
  }

  def importUrl(inputPath: String): String = chenPyUtils.import_url(inputPath).as[String]

  /** This is the `importCode(...)` method exposed on the console. It attempts to find a suitable CPG generator first by
    * looking at the `language` parameter and if no generator is found for the language, looking the contents at
    * `inputPath` to determine heuristically which generator to use.
    */
  def apply(inputPath: String, projectName: String = "", language: String = ""): Cpg = {
    var srcPath =
      if (
        inputPath.startsWith("http") || inputPath.startsWith("git://") || inputPath.startsWith("CVE-") || inputPath
          .startsWith("GHSA-")
      ) importUrl(inputPath)
      else inputPath
    checkInputPath(srcPath)
    if (language != "") {
      generatorFactory.forLanguage(language) match {
        case None           => throw new ConsoleException(s"No Atom generator exists for language: $language")
        case Some(frontend) => apply(frontend, srcPath, projectName)
      }
    } else {
      generatorFactory.forCodeAt(srcPath) match {
        case None           => throw new ConsoleException(s"No suitable Atom generator found for: $srcPath")
        case Some(frontend) => apply(frontend, srcPath, projectName)
      }
    }
  }

  def c: SourceBasedFrontend    = new CFrontend("c")
  def cpp: SourceBasedFrontend  = new CFrontend("cpp", extension = "cpp")
  def java: SourceBasedFrontend = new SourceBasedFrontend("java", Languages.JAVASRC, "Java Source Frontend", "java")
  def jvm: Frontend =
    new BinaryFrontend("jvm", Languages.JAVA, "Java/Dalvik Bytecode Frontend (based on SOOT's jimple)")
  def ghidra: Frontend = new BinaryFrontend("ghidra", Languages.GHIDRA, "ghidra reverse engineering frontend")
  def kotlin: SourceBasedFrontend =
    new SourceBasedFrontend("kotlin", Languages.KOTLIN, "Kotlin Source Frontend", "kotlin")
  def python: SourceBasedFrontend =
    new SourceBasedFrontend("python", Languages.PYTHONSRC, "Python Source Frontend", "py")
  def golang: SourceBasedFrontend = new SourceBasedFrontend("golang", Languages.GOLANG, "Golang Source Frontend", "go")
  def javascript: SourceBasedFrontend =
    new SourceBasedFrontend("javascript", Languages.JAVASCRIPT, "Javascript Source Frontend", "js")
  def jssrc: SourceBasedFrontend =
    new SourceBasedFrontend("jssrc", Languages.JSSRC, "Javascript/Typescript Source Frontend based on astgen", "js")
  def csharp: Frontend          = new BinaryFrontend("csharp", Languages.CSHARP, "C# Source Frontend (Roslyn)")
  def llvm: Frontend            = new BinaryFrontend("llvm", Languages.LLVM, "LLVM Bitcode Frontend")
  def php: SourceBasedFrontend  = new SourceBasedFrontend("php", Languages.PHP, "PHP source frontend", "php")
  def ruby: SourceBasedFrontend = new SourceBasedFrontend("ruby", Languages.RUBYSRC, "Ruby source frontend", "rb")

  private def allFrontends: List[Frontend] =
    List(c, cpp, ghidra, kotlin, java, jvm, javascript, jssrc, golang, llvm, php, python, csharp, ruby)

  // this is only abstract to force people adding frontends to make a decision whether the frontend consumes binaries or source
  abstract class Frontend(val name: String, val language: String, val description: String = "") {
    def cpgGeneratorForLanguage(
      language: String,
      config: FrontendConfig,
      rootPath: Path,
      args: List[String]
    ): Option[CpgGenerator] =
      io.appthreat.console.cpgcreation.cpgGeneratorForLanguage(language, config, rootPath, args)

    def isAvailable: Boolean =
      cpgGeneratorForLanguage(language, config.frontend, config.install.rootPath.path, args = Nil).exists(_.isAvailable)

    def apply(inputPath: String, projectName: String = "", args: List[String] = List()): Cpg = {
      val frontend = cpgGeneratorForLanguage(language, config.frontend, config.install.rootPath.path, args)
        .getOrElse(throw new ConsoleException(s"no atom generator for language=$language available!"))
      new ImportCode(console)(frontend, inputPath, projectName)
    }
  }

  private class BinaryFrontend(name: String, language: String, description: String = "")
      extends Frontend(name, language, description)

  class SourceBasedFrontend(name: String, language: String, description: String, extension: String)
      extends Frontend(name, language, description) {

    def fromString(str: String, args: List[String] = List()): Cpg = {
      withCodeInTmpFile(str, "tmp." + extension) { dir =>
        super.apply(dir.path.toString, args = args)
      } match {
        case Failure(exception) => throw new ConsoleException(s"unable to generate atom from given String", exception)
        case Success(value)     => value
      }
    }
  }
  class CFrontend(name: String, extension: String = "c")
      extends SourceBasedFrontend(name, Languages.NEWC, "Eclipse CDT Based Frontend for C/C++", extension)

  private def withCodeInTmpFile(str: String, filename: String)(f: File => Cpg): Try[Cpg] = {
    val dir = File.newTemporaryDirectory("console")
    val result = Try {
      (dir / filename).write(str)
      f(dir)
    }
    dir.deleteOnExit(swallowIOExceptions = true)
    result
  }

  /** Provide an overview of the available CPG generators (frontends)
    */
  override def toString: String = {
    val cols = List("name", "description", "available")
    val rows = allFrontends.map { frontend =>
      List(frontend.name, frontend.description, frontend.isAvailable.toString)
    }
    "Type `importCode.<language>` to run a specific language frontend\n" +
      "\n" + Table(cols, rows).render
  }

  private def apply(generator: CpgGenerator, inputPath: String, projectName: String): Cpg = {
    checkInputPath(inputPath)

    val name = Option(projectName).filter(_.nonEmpty).getOrElse(deriveNameFromInputPath(inputPath, workspace))
    report(s"Creating project `$name` for code at `$inputPath`")

    val cpgMaybe = workspace.createProject(inputPath, name).flatMap { pathToProject =>
      val frontendCpgOutFile = pathToProject.resolve(nameOfLegacyCpgInProject)
      val frontendAtomPath   = generatorFactory.runGenerator(generator, inputPath, frontendCpgOutFile.toString)
      frontendAtomPath match {
        case Success(_) =>
          console.open(name).flatMap(_.cpg)
        case Failure(exception) =>
          throw new ConsoleException(s"Error creating project for input path: `$inputPath`", exception)
      }
    }

    cpgMaybe
      .map(cpg => console.summary)
      .getOrElse(throw new ConsoleException(s"Error creating project for input path: `$inputPath`"))
    cpgMaybe.get
  }
}
