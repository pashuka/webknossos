/*
 * Copyright (C) 20011-2014 Scalable minds UG (haftungsbeschränkt) & Co. KG. <http://scm.io>
 */
package com.scalableminds.braingames.binary.store

import java.io.{FileNotFoundException, InputStream, OutputStream, FileInputStream, FileOutputStream, File}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import com.scalableminds.braingames.binary.{LoadBlock, SaveBlock, MappingRequest}
import net.liftweb.common.Box
import net.liftweb.common.Failure
import scalax.file.Path
import com.scalableminds.braingames.binary.Logger._
import scalax.file.Path
import com.scalableminds.util.geometry.Point3D

class FileDataStoreActor extends DataStoreActor(new FileDataStore)

/**
 * A data store implementation which uses the hdd as data storage
 */
class FileDataStore extends DataStore {

  import FileDataStore._
  import DataStore._

  /**
   * Loads the due to x,y and z defined block into the cache array and
   * returns it.
   */
  def load(dataInfo: LoadBlock): Future[Box[Array[Byte]]] = {
    val fileSize = dataInfo.dataSource.blockSize * dataInfo.dataLayer.bytesPerElement
    load(knossosBaseDir(dataInfo), dataInfo.dataSource.id, dataInfo.resolution, dataInfo.block, fileSize)
  }

  def load(dataSetDir: Path, dataSetId: String, resolution: Int, block: Point3D, fileSize: Int): Future[Box[Array[Byte]]] = {
    load(knossosFilePath(dataSetDir, dataSetId, resolution, block), Some(fileSize), fuzzyKnossosFile(dataSetDir, dataSetId, resolution, block))
  }

  def load(path: Path, fileSize: Option[Int] = None, fallback: Option[File] = None): Future[Box[Array[Byte]]] = {
    Future {
      try {
        path.fileOption
          .filter(_.exists())
          .orElse(fallback)
          .map { file =>
            inputStreamToByteArray(new FileInputStream(file), fileSize.getOrElse(file.length().toInt))
          }
      } catch {
        case e: FileNotFoundException =>
          logger.info("File data store couldn't find file: " + path.toAbsolute.path)
          Failure("Couldn't find file: " + e)
      }
    }
  }

  def save(dataInfo: SaveBlock): Future[Unit] = {
    save(knossosBaseDir(dataInfo), dataInfo.dataSource.id, dataInfo.resolution, dataInfo.block, dataInfo.data)
  }

  def save(dataSetDir: Path, dataSetId: String, resolution: Int, block: Point3D, data: Array[Byte]): Future[Unit] = {
    Future {
      val path = knossosFilePath(dataSetDir, dataSetId, resolution, block)
      try {
        path.toAbsolute.parent.map(_.createDirectory(failIfExists = false))
        val binaryStream = new FileOutputStream(path.path)
        byteArrayToOutputStream(binaryStream, data)
      } catch {
        case e: FileNotFoundException =>
          logger.error("File datastore couldn't write to file: " + path.toAbsolute.path)
          Failure("Couldn't write to file: " + e)
      }
    }
  }

  def load(request: MappingRequest): Future[Box[Array[Byte]]] = {
    load(knossosMappingFilePath(request))
  }
}

object FileDataStore {
  /**
   * Read file contents to a byteArray
   */
  def inputStreamToByteArray(is: InputStream, dataInfo: LoadBlock): Array[Byte] =
    inputStreamToByteArray(is, dataInfo.dataSource.blockSize * dataInfo.dataLayer.bytesPerElement)

  def inputStreamToByteArray(is: InputStream, size: Int) = {
    val byteArray = new Array[Byte](size)
    is.read(byteArray, 0, size)
    is.close()
    byteArray
  }

  /**
   * Writes bytearray contents to a FileOutputStream
   */
  def byteArrayToOutputStream(os: OutputStream, dataInfo: SaveBlock): Unit =
    byteArrayToOutputStream(os, dataInfo.data)

  def byteArrayToOutputStream(os: OutputStream, data: Array[Byte]): Unit = {
    os.write(data)
    os.close()
  }
}