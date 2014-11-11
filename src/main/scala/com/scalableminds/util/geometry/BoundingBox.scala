/*
 * Copyright (C) 20011-2014 Scalable minds UG (haftungsbeschränkt) & Co. KG. <http://scm.io>
 */
package com.scalableminds.util.geometry

import com.scalableminds.util.tools.Fox
import net.liftweb.common.{Box, Empty, Full}

case class BoundingBox(topLeft: Point3D, width: Int, height: Int, depth: Int) {

  val bottomRight = topLeft.move(width, height, depth)

  def contains(p: Point3D) = {
    p.x >= topLeft.x && p.y >= topLeft.y && p.z >= topLeft.z &&
      p.x <= bottomRight.x && p.y <= bottomRight.y && p.z <= bottomRight.z

  }

  def combineWith(other: BoundingBox) = {
    val x = math.min(other.topLeft.x, topLeft.x)
    val y = math.min(other.topLeft.y, topLeft.y)
    val z = math.min(other.topLeft.z, topLeft.z)

    val w = math.max(other.width, width)
    val h = math.max(other.height, height)
    val d = math.max(other.depth, depth)

    BoundingBox(Point3D(x,y,z), w, h, d)
  }

  def isEmpty =
    width <= 0 || height <= 0 || depth <= 0

  def center =
    topLeft.move(bottomRight).scale(0.5f)

}

object BoundingBox{
  import play.api.libs.json._

  val formRx = "\\s*([0-9]+),\\s*([0-9]+),\\s*([0-9]+)\\s*,\\s*([0-9]+),\\s*([0-9]+),\\s*([0-9]+)\\s*".r

  def toForm(b: BoundingBox) =
    Some("%d, %d, %d, %d, %d, %d".format(
      b.topLeft.x, b.topLeft.y, b.topLeft.z, b.topLeft.x + b.width, b.topLeft.y + b.height, b.topLeft.z + b.depth
    ))

  def fromForm(s: String) = {
    s match {
      case formRx(minX, minY, minZ, maxX, maxY, maxZ) =>
        createFrom(
          Point3D(Integer.parseInt(minX), Integer.parseInt(minY), Integer.parseInt(minZ)),
          Point3D(Integer.parseInt(maxX), Integer.parseInt(maxY), Integer.parseInt(maxZ))
        )
      case _ =>
        null
    }
  }

  def hull(c: List[BoundingBox]) = {
    if (c.isEmpty)
      BoundingBox(Point3D(0, 0, 0), 0, 0, 0)
    else {
      val topLeft = c.map(_.topLeft).foldLeft(Point3D(0, 0, 0))((b, e) => (
        Point3D(math.max(b.x, e.x), math.max(b.y, e.y), math.max(b.z, e.z))))

      BoundingBox(
        topLeft,
        c.map(_.width).max,
        c.map(_.height).max,
        c.map(_.depth).max)
    }
  }

  def combine(bbs: List[BoundingBox]) = {
    bbs match{
      case head :: tail =>
        tail.foldLeft(head)( _ combineWith _)
      case _ =>
        BoundingBox(Point3D(0,0,0), 0, 0, 0)
    }
  }

  def createFrom(bbox: List[List[Int]]): Box[BoundingBox] = {
    if (bbox.size < 3 || bbox(0).size < 2 || bbox(1).size < 2 || bbox(2).size < 2)
      Empty
    else
      Full(BoundingBox(
        Point3D(bbox(0)(0), bbox(1)(0), bbox(2)(0)),
        bbox(0)(1) - bbox(0)(0),
        bbox(1)(1) - bbox(1)(0),
        bbox(2)(1) - bbox(2)(0)))
  }

  def createFrom(topLeft: Point3D, bottomRight: Point3D): Box[BoundingBox] =
    if(topLeft <= bottomRight)
      Full(BoundingBox(topLeft, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y, bottomRight.z - topLeft.z))
    else
      Empty

  def createFrom(width: Int, height: Int, deph: Int, topLeft: Point3D): BoundingBox =
    BoundingBox(topLeft, width, height, deph)

  implicit val boundingBoxFormat = Json.format[BoundingBox]
}
