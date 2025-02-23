package io.appthreat.console

import io.shiftleft.codepropertygraph.generated.Languages
import better.files.File

import java.nio.file.Path
import scala.collection.mutable

package object cpgcreation {

  /** For a given language, return CPG generator script
    */
  def cpgGeneratorForLanguage(
    language: String,
    config: FrontendConfig,
    rootPath: Path,
    args: List[String]
  ): Option[CpgGenerator] = {
    lazy val conf = config.withArgs(args)
    language match {
      case Languages.C | Languages.NEWC | Languages.JAVA | Languages.JAVASRC | Languages.JSSRC | Languages.JAVASCRIPT |
          Languages.PYTHON | Languages.PYTHONSRC =>
        Some(AtomGenerator(conf, rootPath, language))
      case _ => None
    }
  }

  /** Heuristically determines language by inspecting file/dir at path.
    */
  def guessLanguage(path: String): Option[String] = {
    val file = File(path)
    if (file.isDirectory) {
      guessMajorityLanguageInDir(file)
    } else {
      guessLanguageForRegularFile(file)
    }
  }

  /** Guess the main language for an entire directory (e.g. a whole project), based on a group count of all individual
    * files. Rationale: many projects contain files from different languages, but most often one language is standing
    * out in numbers.
    */
  private def guessMajorityLanguageInDir(directory: File): Option[String] = {
    assert(directory.isDirectory, s"$directory must be a directory, but wasn't")
    val groupCount = mutable.Map.empty[String, Int].withDefaultValue(0)

    for {
      file <- directory.listRecursively
      if file.isRegularFile
      guessedLanguage <- guessLanguageForRegularFile(file)
    } {
      val oldValue = groupCount(guessedLanguage)
      groupCount.update(guessedLanguage, oldValue + 1)
    }

    groupCount.toSeq.sortBy(_._2).lastOption.map(_._1)
  }

  private def isJavaBinary(filename: String): Boolean =
    Seq(".jar", ".war", ".ear", ".apk").exists(filename.endsWith)

  private def isCsharpFile(filename: String): Boolean =
    Seq(".csproj", ".cs").exists(filename.endsWith)

  private def isGoFile(filename: String): Boolean =
    filename.endsWith(".go") || Set("gopkg.lock", "gopkg.toml", "go.mod", "go.sum").contains(filename)

  private def isLlvmFile(filename: String): Boolean =
    Seq(".bc", ".ll").exists(filename.endsWith)

  private def isJsFile(filename: String): Boolean =
    Seq(".js", ".ts", ".jsx", ".tsx").exists(filename.endsWith) || filename == "package.json"

  /** check if given filename looks like it might be a C/CPP source or header file mostly copied from
    * io.appthreat.c2cpg.parser.FileDefaults
    */
  private def isCFile(filename: String): Boolean =
    Seq(".c", ".cc", ".cpp", ".h", ".hpp", ".hh").exists(filename.endsWith)

  private def isYamlFile(filename: String): Boolean =
    Seq(".yml", ".yaml").exists(filename.endsWith)

  private def isBomFile(filename: String): Boolean =
    Seq("bom.json", ".cdx.json").exists(filename.endsWith)

  private def guessLanguageForRegularFile(file: File): Option[String] = {
    file.name.toLowerCase match {
      case f if isJavaBinary(f)      => Some(Languages.JAVA)
      case f if isCsharpFile(f)      => Some(Languages.CSHARP)
      case f if isGoFile(f)          => Some(Languages.GOLANG)
      case f if isJsFile(f)          => Some(Languages.JSSRC)
      case f if f.endsWith(".java")  => Some(Languages.JAVASRC)
      case f if f.endsWith(".class") => Some(Languages.JAVA)
      case f if f.endsWith(".kt")    => Some(Languages.KOTLIN)
      case f if f.endsWith(".php")   => Some(Languages.PHP)
      case f if f.endsWith(".py")    => Some(Languages.PYTHONSRC)
      case f if f.endsWith(".rb")    => Some(Languages.RUBYSRC)
      case f if isLlvmFile(f)        => Some(Languages.LLVM)
      case f if isCFile(f)           => Some(Languages.NEWC)
      case f if isBomFile(f)         => Option("BOM")
      case f if f.endsWith(".json")  => Option("JSON")
      case f if isYamlFile(f)        => Option("YAML")
      case _                         => None
    }
  }

}
