package io.appthreat.console.cpgcreation

import better.files.Dsl.*
import better.files.File
import io.appthreat.console.ConsoleConfig
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, CpgLoaderConfig}
import io.shiftleft.codepropertygraph.generated.Languages
import overflowdb.Config

import java.nio.file.Path
import scala.util.Try

object CpgGeneratorFactory {
  private val KNOWN_LANGUAGES = Set(
    Languages.C,
    Languages.CSHARP,
    Languages.GOLANG,
    Languages.GHIDRA,
    Languages.JAVA,
    Languages.JAVASCRIPT,
    Languages.JSSRC,
    Languages.PYTHON,
    Languages.PYTHONSRC,
    Languages.LLVM,
    Languages.PHP,
    Languages.KOTLIN,
    Languages.NEWC,
    Languages.JAVASRC
  )
}

class CpgGeneratorFactory(config: ConsoleConfig) {

  /** For a given input path, try to guess a suitable generator and return it
    */
  def forCodeAt(inputPath: String): Option[CpgGenerator] =
    for {
      language     <- guessLanguage(inputPath)
      cpgGenerator <- cpgGeneratorForLanguage(language, config.frontend, config.install.rootPath.path, args = Nil)
    } yield {
      cpgGenerator
    }

  /** For a language, return the generator
    */
  def forLanguage(language: String): Option[CpgGenerator] = {
    Option(language)
      .filter(languageIsKnown)
      .flatMap { lang =>
        cpgGeneratorForLanguage(lang, config.frontend, config.install.rootPath.path, args = Nil)
      }
  }

  def languageIsKnown(language: String): Boolean = CpgGeneratorFactory.KNOWN_LANGUAGES.contains(language)

  def runGenerator(generator: CpgGenerator, inputPath: String, outputPath: String): Try[Path] = {
    val outputFileOpt: Try[File] =
      generator.generate(inputPath, outputPath).map(File(_))
    outputFileOpt.map { outFile =>
      val parentPath = outFile.parent.path.toAbsolutePath
      if (isZipFile(outFile)) {
        val srcFilename = outFile.path.toAbsolutePath.toString
        val dstFilename = parentPath.resolve("cpg.bin").toAbsolutePath.toString
        // MemoryHelper.hintForInsufficientMemory(srcFilename).map(report)
        convertProtoCpgToOverflowDb(srcFilename, dstFilename)
      } else {
        val srcPath = parentPath.resolve("app.atom")
        if (srcPath.toFile.exists()) {
          mv(srcPath, parentPath.resolve("cpg.bin"))
        }
      }
      parentPath
    }
  }

  def convertProtoCpgToOverflowDb(srcFilename: String, dstFilename: String): Unit = {
    val odbConfig = Config.withDefaults.withStorageLocation(dstFilename)
    val config    = CpgLoaderConfig.withDefaults.doNotCreateIndexesOnLoad.withOverflowConfig(odbConfig)
    CpgLoader.load(srcFilename, config).close
    File(srcFilename).delete()
  }

  def isZipFile(file: File): Boolean = {
    val bytes = file.bytes
    Try {
      bytes.next() == 'P' && bytes.next() == 'K'
    }.getOrElse(false)
  }

  private def report(str: String): Unit = System.err.println(str)

}
