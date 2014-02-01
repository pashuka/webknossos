package models.user

import play.api.Play.current
import models.context._
import braingames.security.SCrypt._
import scala.collection.mutable.Stack
import play.api.libs.json.{Json, JsValue}
import play.api.libs.json.Json._
import scala.collection.immutable.HashMap
import models.basics._
import models.user.Experience._
import models.team._
import braingames.reactivemongo.{DBAccessContext, DBAccessContextPayload}
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.api.indexes.Index
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.concurrent.Execution.Implicits._

case class User(
                 email: String,
                 firstName: String,
                 lastName: String,
                 verified: Boolean = false,
                 pwdHash: String = "",
                 teams: List[TeamMembership],
                 configuration: UserSettings = UserSettings.defaultSettings,
                 experiences: Map[String, Int] = Map.empty,
                 lastActivity: Long = System.currentTimeMillis,
                 _id: BSONObjectID = BSONObjectID.generate) extends DBAccessContextPayload {

  val dao = User

  //lazy val teamTrees = TeamTreeDAO.findAllTeams(_groups)(GlobalAccessContext)

  def teamsWithRole(role: Role) = teams.filter(_.role == role)

  def teamNames = teams.map(_.team)

  val name = firstName + " " + lastName

  val abreviatedName = (firstName.take(1) + lastName) toLowerCase

  lazy val id = _id.stringify

  lazy val adminTeams = teamsWithRole(Role.Admin)

  lazy val adminTeamNames = adminTeams.map(_.team)

  lazy val hasAdminAccess = !adminTeams.isEmpty

  def roleInTeam(team: String) = teams.find(_.team == team).map(_.role)

  override def toString = email

  def setExperience(name: String, value: Int) = {
    val n = name.trim
    this.copy(experiences = this.experiences + (n -> value))
  }

  def increaseExperience(name: String, value: Int) = {
    val n = name.trim
    this.copy(experiences = this.experiences + (n -> (this.experiences.get(n).getOrElse(0) + value)))
  }

  def deleteExperience(name: String) = {
    val n = name.trim
    this.copy(experiences = this.experiences.filterNot(_._1 == n))
  }

  def logActivity(time: Long) =
    this.copy(lastActivity = time)

  def verify =
    this.copy(verified = true)

  def addTeam(teamMemberships: List[TeamMembership]) =
    this.copy(teams = teamMemberships ::: teams)

  def removeTeam(team: String) =
    this.copy(teams = teams.filterNot(_.team == team))

  def lastActivityDays =
    (System.currentTimeMillis - this.lastActivity) / (1000 * 60 * 60 * 24)
}

object User {
  private[user] val userFormat = Json.format[User]

  val userPublicWrites: Writes[User] =
    ((__ \ "id").write[String] and
      (__ \ "email").write[String] and
      (__ \ "firstName").write[String] and
      (__ \ "lastName").write[String] and
      (__ \ "verified").write[Boolean] and
      (__ \ "teams").write[List[TeamMembership]] and
      (__ \ "experiences").write[Map[String, Int]] and
      (__ \ "lastActivity").write[Long])(u =>
      (u.id, u.email, u.firstName, u.lastName, u.verified, u.teams, u.experiences, u.lastActivity))
}

object UserDAO extends SecuredBaseDAO[User] {

  val collectionName = "users"

  val formatter = User.userFormat

  collection.indexesManager.ensure(Index(Seq("email" -> IndexType.Ascending)))

  override def findQueryFilter(implicit ctx: DBAccessContext) = {
    ctx.data match {
      case Some(user: User) =>
        AllowIf(Json.obj("$or" -> Json.arr(
          Json.obj("teams.team" -> Json.obj("$in" -> user.teamNames)),
          Json.obj("teams" -> Json.arr()))))
      case _ =>
        DenyEveryone()
    }
  }

  override def removeQueryFilter(implicit ctx: DBAccessContext) = {
    ctx.data match {
      case Some(user: User) =>
        AllowIf(Json.obj("teams.team" -> Json.obj("$in" -> user.adminTeamNames)))
      case _ =>
        DenyEveryone()
    }
  }

  def findAllInTeams(teams: List[String])(implicit ctx: DBAccessContext) =
    collectionFind(Json.obj("teams.team" -> Json.obj("$in" -> teams)))
      .cursor[User](formatter, defaultContext)
      .collect[List]()

  def findOneByEmail(email: String)(implicit ctx: DBAccessContext) = findOne("email", email)

  def findByIdQ(id: BSONObjectID) = Json.obj("_id" -> id)

  def authRemote(email: String, loginType: String)(implicit ctx: DBAccessContext) =
    findOne(Json.obj("email" -> email, "loginType" -> loginType))

  def auth(email: String, password: String)(implicit ctx: DBAccessContext): Future[Option[User]] =
    findOneByEmail(email).map(_.filter(user => verifyPassword(password, user.pwdHash)))

  def insert(user: User, isVerified: Boolean)(implicit ctx: DBAccessContext): Future[User] = {
    if (isVerified) {
      val u = user.verify
      insert(u).map(_ => u)
    } else
      insert(user).map(_ => user)
  }

  def addTeams(_user: BSONObjectID, teams: Seq[TeamMembership])(implicit ctx: DBAccessContext) =
    collectionUpdate(findByIdQ(_user), Json.obj("$pushAll" -> Json.obj("teams" -> teams)))

  def addRole(_user: BSONObjectID, role: String)(implicit ctx: DBAccessContext) =
    collectionUpdate(findByIdQ(_user), Json.obj("$push" -> Json.obj("roles" -> role)))

  def deleteRole(_user: BSONObjectID, role: String)(implicit ctx: DBAccessContext) =
    collectionUpdate(findByIdQ(_user), Json.obj("$pull" -> Json.obj("roles" -> role)))

  def increaseExperience(_user: BSONObjectID, domain: String, value: Int)(implicit ctx: DBAccessContext) = {
    collectionUpdate(findByIdQ(_user), Json.obj("$inc" -> Json.obj(s"experiences.$domain" -> value)))
  }

  def updateSettings(user: User, settings: UserSettings)(implicit ctx: DBAccessContext) = {
    collectionUpdate(findByIdQ(user._id), Json.obj("$set" -> Json.obj("configuration.settings" -> settings.settings)))
  }

  def setExperience(_user: BSONObjectID, domain: String, value: Int)(implicit ctx: DBAccessContext) = {
    collectionUpdate(findByIdQ(_user), Json.obj("$set" -> Json.obj(s"experiences.$domain" -> value)))
  }

  def deleteExperience(_user: BSONObjectID, domain: String)(implicit ctx: DBAccessContext) = {
    collectionUpdate(findByIdQ(_user), Json.obj("$unset" -> Json.obj(s"experiences.$domain" -> 1)))
  }

  def logActivity(user: User, lastActivity: Long)(implicit ctx: DBAccessContext) = {
    collectionUpdate(findByIdQ(user._id), Json.obj("$set" -> Json.obj("lastActivity" -> lastActivity)))
  }

  def verify(user: User)(implicit ctx: DBAccessContext) = {
    collectionUpdate(
      Json.obj("email" -> user.email),
      Json.obj("$set" -> Json.obj("verified" -> true)))
  }
}
