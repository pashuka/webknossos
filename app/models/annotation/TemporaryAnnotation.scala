package models.annotation

import com.scalableminds.util.io.NamedStream
import play.api.libs.concurrent.Execution.Implicits._
import reactivemongo.bson.BSONObjectID
import com.scalableminds.util.reactivemongo.DBAccessContext
import play.api.libs.json.JsValue
import models.user.User

/**
 * Company: scalableminds
 * User: tmbo
 * Date: 01.06.13
 * Time: 03:05
 */

import models.annotation.AnnotationType._

import scala.concurrent.Future
import com.scalableminds.util.tools.Fox
import com.scalableminds.util.io.NamedFileStream

case class TemporaryAnnotation(
                                id: String,
                                _user: Option[BSONObjectID],
                                _content: () => Fox[AnnotationContent],
                                _task: Option[BSONObjectID] = None,
                                team: String,
                                relativeDownloadUrl: Option[String],
                                state: AnnotationState = AnnotationState.Finished,
                                typ: AnnotationType = AnnotationType.CompoundProject,
                                _name: Option[String] = None,
                                description: String = "",
                                restrictions: AnnotationRestrictions = AnnotationRestrictions.restrictEverything,
                                version: Int = 0,
                                created: Long = System.currentTimeMillis,
                                isPublic: Boolean = false
                                ) extends AnnotationLike {

  def incrementVersion = this.copy(version = version + 1)

  type Self = TemporaryAnnotation

  lazy val content = _content()

  def tracingTime = None    // We currently do not tracing time on temporary annotations

  def tags = Set.empty

  def muta = new TemporaryAnnotationMutations(this)

  def temporaryDuplicate(keepId: Boolean)(implicit ctx: DBAccessContext) = {
    val temp = if (keepId) this.copy() else this.copy(id = BSONObjectID.generate.stringify)
    Fox.successful(temp)
  }

  def makeReadOnly: AnnotationLike = {
    this.copy(restrictions = AnnotationRestrictions.readonlyAnnotation())
  }

  def saveToDB(implicit ctx: DBAccessContext): Fox[Annotation] = {
    for{
      c <- content
      savedContent <- c.saveToDB
      annotationId = BSONObjectID.parse(id).getOrElse(BSONObjectID.generate)
      annotation <- AnnotationService.createFrom(this, savedContent, annotationId)
    } yield annotation
  }
}

object TemporaryAnnotationService {
  def createFrom(a: Annotation, id: String, _content: AnnotationContent): TemporaryAnnotation = {
    val content = () => Fox.successful(_content)
    TemporaryAnnotation(id, a._user, content, a._task, a.team, a.relativeDownloadUrl, a.state, a.typ, a._name, a.description, a.restrictions, a.version, a.created)
  }
}

class TemporaryAnnotationMutations(annotation: TemporaryAnnotation) extends AnnotationMutationsLike {
  type AType = TemporaryAnnotation

  def resetToBase()(implicit ctx: DBAccessContext): Fox[TemporaryAnnotationMutations#AType] = ???

  def reopen()(implicit ctx: DBAccessContext): Fox[TemporaryAnnotationMutations#AType] = ???

  def updateFromJson(js: Seq[JsValue])(implicit ctx: DBAccessContext): Fox[TemporaryAnnotationMutations#AType] = ???

  def cancelTask()(implicit ctx: DBAccessContext): Fox[TemporaryAnnotationMutations#AType] = ???

  def loadAnnotationContent()(implicit ctx: DBAccessContext): Fox[NamedStream] = ???
}
