package models.annotation.nml

import java.io.{File, FileInputStream, InputStream}
import java.nio.file.{Files, StandardCopyOption}

import com.scalableminds.util.io.ZipIO
import com.scalableminds.webknossos.datastore.SkeletonTracing.{SkeletonTracing, TreeGroup}
import com.scalableminds.webknossos.datastore.VolumeTracing.VolumeTracing
import com.typesafe.scalalogging.LazyLogging
import javax.inject.Inject
import models.annotation.nml.NmlResults._
import net.liftweb.common.{Empty, Failure, Full}
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}

import scala.concurrent.ExecutionContext


class NmlService @Inject()(temporaryFileCreator: TemporaryFileCreator)(implicit ec: ExecutionContext) extends LazyLogging {

  def extractFromNml(file: File, name: String): NmlParseResult = {
    extractFromNml(new FileInputStream(file), name)
  }

  def extractFromNml(inputStream: InputStream, name: String): NmlParseResult = {
    NmlParser.parse(name, inputStream) match {
      case Full((skeletonTracing, volumeTracingWithDataLocation, description)) => NmlParseSuccess(name, skeletonTracing, volumeTracingWithDataLocation, description)
      case Failure(msg, _, _) => NmlParseFailure(name, msg)
      case Empty => NmlParseEmpty(name)
    }
  }

  def extractFromZip(file: File, zipFileName: Option[String] = None): ZipParseResult = {
    val name = zipFileName getOrElse file.getName
    var otherFiles = Map.empty[String, TemporaryFile]
    var parseResults = List.empty[NmlParseResult]
    ZipIO.withUnziped(file, includeHiddenFiles = false) { (filename, file) =>
      if (filename.toString.endsWith(".nml")) {
        val result = extractFromNml(file, filename.toString)
        parseResults ::= result
      } else {
        val tempFile = temporaryFileCreator.create(filename.toString)
        Files.copy(file, tempFile.path, StandardCopyOption.REPLACE_EXISTING)
        otherFiles += (filename.toString -> tempFile)
      }
    }
    ZipParseResult(parseResults, otherFiles)
  }

  def addPrefixesToTreeNames(parseResults: List[NmlParseResult]): List[NmlParseResult] = {
    def renameTrees(name: String, tracing: SkeletonTracing): SkeletonTracing = {
      val prefix = name.replaceAll("\\.[^.]*$", "") + "_"
      tracing.copy(trees = tracing.trees.map(tree => tree.copy(name = prefix + tree.name)))
    }

    if (parseResults.length > 1) {
      parseResults.map(r =>
        r match {
          case NmlParseSuccess(name, Some(skeletonTracing), volumeTracingOpt, description) =>
            NmlParseSuccess(name, Some(renameTrees(name, skeletonTracing)), volumeTracingOpt, description)
          case _ => r
        }
      )
    } else {
      parseResults
    }
  }

  def wrapTreesInGroups(parseResults: List[NmlParseResult]): List[NmlParseResult] = {
    def getMaximumGroupId(treeGroups: Seq[TreeGroup]) = if (treeGroups.isEmpty) 0 else treeGroups.map(_.groupId).max

    def wrapTreesInGroup(name: String, tracing: SkeletonTracing): SkeletonTracing = {
      val unusedGroupId = getMaximumGroupId(tracing.treeGroups) + 1
      val newTrees = tracing.trees.map(tree => tree.copy(groupId = Some(tree.groupId.getOrElse(unusedGroupId))))
      val newTreeGroups = Seq(TreeGroup(name, unusedGroupId, tracing.treeGroups))
      tracing.copy(trees = newTrees, treeGroups = newTreeGroups)
    }

    if (parseResults.length > 1) {
      parseResults.map(r =>
        r match {
          case NmlParseSuccess(name, Some(skeletonTracing), volumeTracingOpt, description) =>
            NmlParseSuccess(name, Some(wrapTreesInGroup(name, skeletonTracing)), volumeTracingOpt, description)
          case _ => r
        }
      )
    } else {
      parseResults
    }
  }

  def extractFromFiles(files: Seq[(File, String)]): ZipParseResult = {
    files.foldLeft(NmlResults.ZipParseResult()) {
      case (acc, next) => acc.combineWith(extractFromFile(next._1, next._2))
    }
  }

  def extractFromFile(file: File, fileName: String): ZipParseResult = {
    if (fileName.endsWith(".zip")) {
      logger.trace("Extracting from Zip file")
      extractFromZip(file, Some(fileName))
    } else {
      logger.trace("Extracting from Nml file")
      val parseResult = extractFromNml(file, fileName)
      ZipParseResult(List(parseResult), Map.empty)
    }
  }

  def splitVolumeAndSkeletonTracings(tracings: List[(Option[SkeletonTracing], Option[(VolumeTracing, String)])]): (List[SkeletonTracing], List[(VolumeTracing, String)]) = {
    val skeletons = tracings.flatMap(_._1)
    val volumes = tracings.flatMap(_._2)
    (skeletons, volumes)
  }
}
