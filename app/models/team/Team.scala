package models.team

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import braingames.reactivemongo.{DBAccessContext}
import models.basics.SecuredBaseDAO
import braingames.util.FoxImplicits
import models.user.{UserDAO, UserService, User}
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError
import scala.util.{Failure, Success}

case class Team(name: String, roles: List[Role], owner: Option[BSONObjectID] = None) {
  def isEditableBy(user: User) =
    user.adminTeamNames.contains(name)
}

object Team extends {

  val teamFormat = Json.format[Team]

  def teamPublicWrites(requestingUser: User): Writes[Team] =
    ((__ \ "name").write[String] and
      (__ \ "roles").write[List[Role]] and
      (__ \ "owner").write[String] and
      (__ \ "isEditable").write[Boolean])(t =>
      (t.name, t.roles, t.owner.map(_.stringify) getOrElse "", t.isEditableBy(requestingUser)))

  def teamPublicReads(requestingUser: User): Reads[Team] =
    ((__ \ "name").read[String] and
      (__ \ "roles").read[List[Role]]
      )( (name, roles) => Team(name, roles, Some(requestingUser._id)))
}

object TeamService{
  def create(team: Team, user: User)(implicit ctx: DBAccessContext) = {
    UserDAO.addTeams(user._id, Seq(TeamMembership(team.name, Role.Admin)))
    TeamDAO.insert(team)
  }
}

object TeamDAO extends SecuredBaseDAO[Team] with FoxImplicits {
  val collectionName = "teams"

  implicit val formatter = Team.teamFormat

  def findOneByName(name: String)(implicit ctx: DBAccessContext) =
    findOne("name", name)

}