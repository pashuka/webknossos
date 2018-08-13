package models.binary


import com.scalableminds.util.geometry.{BoundingBox, Point3D, Scale}
import com.scalableminds.util.accesscontext.{DBAccessContext, GlobalAccessContext}
import com.scalableminds.util.tools.{Fox, FoxImplicits, JsonHelper}
import com.scalableminds.webknossos.datastore.models.datasource.inbox.{UnusableDataSource, InboxDataSourceLike => InboxDataSource}
import com.scalableminds.webknossos.datastore.models.datasource.{AbstractDataLayer, AbstractSegmentationLayer, Category, DataSourceId, ElementClass, GenericDataSource, DataLayerLike => DataLayer}
import com.scalableminds.webknossos.schema.Tables._
import models.configuration.DataSetConfiguration
import models.team._
import models.user.User
import net.liftweb.common.Full
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.utils.UriEncoding
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.TransactionIsolation.Serializable
import slick.lifted.Rep
import utils.{ObjectId, SQLDAO, SimpleSQLDAO}


case class DataSet(
                       _id: ObjectId,
                       _dataStore: String,
                       _organization: ObjectId,
                       defaultConfiguration: Option[DataSetConfiguration] = None,
                       description: Option[String] = None,
                       displayName: Option[String] = None,
                       isPublic: Boolean,
                       isUsable: Boolean,
                       name: String,
                       scale: Option[Scale],
                       sharingToken: Option[String],
                       status: String,
                       logoUrl: Option[String],
                       created: Long = System.currentTimeMillis(),
                       isDeleted: Boolean = false
                     ) extends FoxImplicits {

  def getDataLayerByName(dataLayerName: String)(implicit ctx: DBAccessContext): Fox[DataLayer] =
    DataSetDataLayerDAO.findOneByNameForDataSet(dataLayerName, _id)

  def getLogoUrl: Fox[String] =
    logoUrl match {
      case Some(url) => Fox.successful(url)
      case None => OrganizationDAO.findOne(_organization)(GlobalAccessContext).map(_.logoUrl)
    }

  def organization: Fox[Organization] =
    OrganizationDAO.findOne(_organization)(GlobalAccessContext) ?~> Messages("organization.notFound")

  def dataStore: Fox[DataStore] =
    DataStoreDAO.findOneByName(_dataStore.trim)(GlobalAccessContext) ?~> Messages("datastore.notFound")

  def dataStoreInfo: Fox[DataStoreInfo] =
    for {
      dataStore <- dataStore
    } yield DataStoreInfo(dataStore.name, dataStore.url, dataStore.typ)

  def dataStoreHandler(implicit ctx: DBAccessContext): Fox[DataStoreHandlingStrategy] =
    for {
      dataStoreInfo <- dataStoreInfo
    } yield {
      dataStoreInfo.typ match {
        case WebKnossosStore => new WKStoreHandlingStrategy(dataStoreInfo, this)
      }
    }

  def urlEncodedName: String =
    UriEncoding.encodePathSegment(name, "UTF-8")

  def lastUsedByUser(userOpt: Option[User])(implicit ctx: DBAccessContext): Fox[Long] = {
    userOpt match {
      case Some(user) =>
        (for {
          lastUsedTime <- DataSetLastUsedTimesDAO.findForDataSetAndUser(this._id, user._id).futureBox
        } yield lastUsedTime.toOption.getOrElse(0L)).toFox
      case _ => Fox.successful(0L)
    }
  }

  def lastUsedByUser(user: User)(implicit ctx: DBAccessContext): Fox[Long] =
    lastUsedByUser(Some(user))

  def isEditableBy(userOpt: Option[User])(implicit ctx: DBAccessContext): Fox[Boolean] = {
    userOpt match {
      case Some(user) =>
        for {
          isTeamManagerInOrg <- user.isTeamManagerInOrg(_organization)
        } yield (user.isAdminOf(_organization) || isTeamManagerInOrg)
      case _ => Fox.successful(false)
    }
  }

  def isEditableBy(user: User)(implicit ctx: DBAccessContext): Fox[Boolean] =
    isEditableBy(Some(user))

  def allowedTeamIds =
    DataSetAllowedTeamsDAO.findAllForDataSet(_id)(GlobalAccessContext) ?~> Messages("allowedTeams.notFound")

  def allowedTeams =
    for {
      allowedTeamIds <- allowedTeamIds
      allowedTeams <- Fox.combined(allowedTeamIds.map(TeamDAO.findOne(_)(GlobalAccessContext)))
    } yield allowedTeams

  def constructDataSource(implicit ctx: DBAccessContext): Fox[InboxDataSource] = {
    for {
      organization <- organization
      dataLayersBox <- (DataSetDataLayerDAO.findAllForDataSet(_id) ?~> "could not find data layers").futureBox
      dataSourceId = DataSourceId(name, organization.name)
    } yield {
      dataLayersBox match {
        case Full(dataLayers) if (dataLayers.length > 0) =>
          for {
            scale <- scale
          } yield GenericDataSource[DataLayer](dataSourceId, dataLayers, scale)
        case _ =>
          Some(UnusableDataSource[DataLayer](dataSourceId, status, scale))
      }
    }
  }

  def publicWrites(user: Option[User]): Fox[JsObject] = {
    implicit val ctx = GlobalAccessContext
    for {
      teams <- allowedTeams
      teamsJs <- Fox.serialCombined(teams)(_.publicWrites)
      logoUrl <- getLogoUrl
      isEditable <- isEditableBy(user)
      lastUsedByUser <- lastUsedByUser(user)
      dataStoreInfo <- dataStoreInfo
      organization <- organization
      dataSource <- constructDataSource
    } yield {
      Json.obj("name" -> name,
        "dataSource" -> dataSource,
        "dataStore" -> dataStoreInfo,
        "owningOrganization" -> organization.name,
        "allowedTeams" -> teamsJs,
        "isActive" -> isUsable,
        "isPublic" -> isPublic,
        "description" -> description,
        "displayName" -> displayName,
        "created" -> created,
        "isEditable" -> isEditable,
        "lastUsedByUser" -> lastUsedByUser,
        "logoUrl" -> logoUrl)
    }
  }
}

object DataSetDAO extends SQLDAO[DataSet, DatasetsRow, Datasets] {
  val collection = Datasets

  def idColumn(x: Datasets): Rep[String] = x._Id

  def isDeletedColumn(x: Datasets): Rep[Boolean] = x.isdeleted

  private def parseScaleOpt(literalOpt: Option[String]): Fox[Option[Scale]] = literalOpt match {
    case Some(literal) => for {
      scale <- Scale.fromList(parseArrayTuple(literal).map(_.toFloat)) ?~> "could not parse edit position"
    } yield Some(scale)
    case None => Fox.successful(None)
  }

  private def writeScaleLiteral(scale: Scale): String =
    writeStructTuple(List(scale.x, scale.y, scale.z).map(_.toString))

  def parse(r: DatasetsRow): Fox[DataSet] = {
    for {
      scale <- parseScaleOpt(r.scale)
      defaultConfigurationOpt <- Fox.runOptional(r.defaultconfiguration)(JsonHelper.parseJsonToFox[DataSetConfiguration](_))
    } yield {
      DataSet(
        ObjectId(r._Id),
        r._Datastore.trim,
        ObjectId(r._Organization),
        defaultConfigurationOpt,
        r.description,
        r.displayname,
        r.ispublic,
        r.isusable,
        r.name,
        scale,
        r.sharingtoken,
        r.status,
        r.logourl,
        r.created.getTime,
        r.isdeleted
      )
    }
  }

  override def anonymousReadAccessQ(sharingToken: Option[String]) = s"isPublic" + sharingToken.map(t => s" or sharingToken = '$t'").getOrElse("")

  override def readAccessQ(requestingUserId: ObjectId) =
    s"""isPublic
        or _organization in (select _organization from webknossos.users_ where _id = '${requestingUserId.id}' and isAdmin)
        or _id in (select _dataSet
          from (webknossos.dataSet_allowedTeams dt join (select _team from webknossos.user_team_roles where _user = '${requestingUserId.id}') ut on dt._team = ut._team))
        or ('${requestingUserId.id}' in (select _user from webknossos.user_team_roles where isTeammanager)
            and _organization in (select _organization from webknossos.users_ where _id = '${requestingUserId.id}'))"""

  override def findOne(id: ObjectId)(implicit ctx: DBAccessContext): Fox[DataSet] =
    for {
      accessQuery <- readAccessQuery
      rList <- run(sql"select #${columns} from #${existingCollectionName} where _id = ${id.id} and #${accessQuery}".as[DatasetsRow])
      r <- rList.headOption.toFox ?~> ("Could not find object " + id + " in " + collectionName)
      parsed <- parse(r) ?~> ("SQLDAO Error: Could not parse database row for object " + id + " in " + collectionName)
    } yield parsed

  override def findAll(implicit ctx: DBAccessContext): Fox[List[DataSet]] = {
    for {
      accessQuery <- readAccessQuery
      r <- run(sql"select #${columns} from #${existingCollectionName} where #${accessQuery}".as[DatasetsRow])
      parsed <- Fox.combined(r.toList.map(parse))
    } yield parsed
  }

  def findOneByName(name: String)(implicit ctx: DBAccessContext): Fox[DataSet] =
    for {
      accessQuery <- readAccessQuery
      rList <- run(sql"select #${columns} from #${existingCollectionName} where name = ${name} and #${accessQuery}".as[DatasetsRow])
      r <- rList.headOption.toFox
      parsed <- parse(r)
    } yield {
      parsed
    }

  def getIdByName(name: String)(implicit ctx: DBAccessContext): Fox[ObjectId] =
    for {
      accessQuery <- readAccessQuery
      rList <- run(sql"select _id from #${existingCollectionName} where name = ${name} and #${accessQuery}".as[String])
      r <- rList.headOption.toFox
    } yield ObjectId(r)

  def getNameById(id: ObjectId)(implicit ctx: DBAccessContext): Fox[String] =
    for {
      accessQuery <- readAccessQuery
      rList <- run(sql"select name from #${existingCollectionName} where _id = ${id} and #${accessQuery}".as[String])
      r <- rList.headOption.toFox
    } yield r

  def getSharingTokenByName(name: String)(implicit ctx: DBAccessContext): Fox[Option[String]] = {
    for {
      accessQuery <- readAccessQuery
      rList <- run(sql"select sharingToken from webknossos.datasets_ where name = ${name} and #${accessQuery}".as[Option[String]])
      r <- rList.headOption.toFox
    } yield {
      r
    }
  }

  def updateSharingTokenByName(name: String, sharingToken: Option[String])(implicit ctx: DBAccessContext): Fox[Unit] = {
    for {
      accessQuery <- readAccessQuery
      _ <- run(sqlu"update webknossos.datasets_ set sharingToken = ${sharingToken} where name = ${name} and #${accessQuery}")
    } yield ()
  }

  def updateFields(_id: ObjectId, description: Option[String], displayName: Option[String], isPublic: Boolean)(implicit ctx: DBAccessContext): Fox[Unit] = {
    val q = for {row <- Datasets if (notdel(row) && row._Id === _id.id)} yield (row.description, row.displayname, row.ispublic)
    for {
      _ <- run(q.update(description, displayName, isPublic))
    } yield ()
  }

  def updateDefaultConfigurationByName(name: String, configuration: DataSetConfiguration)(implicit ctx: DBAccessContext): Fox[Unit] = {
    for {
      _ <- run(sqlu"""update webknossos.dataSets
                      set defaultConfiguration = '#${sanitize(Json.toJson(configuration).toString)}'
                      where name = ${name}""")
    } yield ()
  }

  def insertOne(d: DataSet)(implicit ctx: DBAccessContext): Fox[Unit] = {
    val defaultConfiguration: Option[String] = d.defaultConfiguration.map(c => Json.toJson(c.configuration).toString)
    for {
      _ <- run(
        sqlu"""insert into webknossos.dataSets(_id, _dataStore, _organization, defaultConfiguration, description, displayName, isPublic, isUsable, name, scale, status, sharingToken, created, isDeleted)
               values(${d._id.id}, ${d._dataStore}, ${d._organization.id}, #${optionLiteral(defaultConfiguration.map(sanitize))}, ${d.description}, ${d.displayName}, ${d.isPublic}, ${d.isUsable},
                      ${d.name}, #${optionLiteral(d.scale.map(s => writeScaleLiteral(s)))}, ${d.status.take(1024)}, ${d.sharingToken}, ${new java.sql.Timestamp(d.created)}, ${d.isDeleted})
            """)
    } yield ()
  }

  def updateDataSourceByName(name: String, dataStoreName: String, source: InboxDataSource, isUsable: Boolean)(implicit ctx: DBAccessContext): Fox[Unit] = {

    for {
      old <- findOneByName(name)
      organization <- OrganizationDAO.findOneByName(source.id.team)
      q =
      sqlu"""update webknossos.dataSets
                    set _dataStore = ${dataStoreName},
                        _organization = ${organization._id.id},
                        isUsable = ${isUsable},
                        scale = #${optionLiteral(source.scaleOpt.map(s => writeScaleLiteral(s)))},
                        status = ${source.statusOpt.getOrElse("")}
                   where _id = ${old._id.id}"""
      _ <- run(q)
      _ <- DataSetDataLayerDAO.updateLayers(old._id, source)
    } yield ()
  }

  def deactivateUnreported(names: List[String], dataStoreName: String): Fox[Unit] = {
    val inclusionPredicate = if (names.isEmpty) "true" else s"name not in ${writeStructTupleWithQuotes(names.map(sanitize))}"
    val deleteResolutionsQuery =
      sqlu"""delete from webknossos.dataSet_resolutions where _dataSet in
            (select _id from webknossos.dataSets where _dataStore = ${dataStoreName}
             and #${inclusionPredicate})"""
    val deleteLayersQuery =
      sqlu"""delete from webknossos.dataSet_layers where _dataSet in
            (select _id from webknossos.dataSets where _dataStore = ${dataStoreName}
             and #${inclusionPredicate})"""
    val setToUnusableQuery =
      sqlu"""update webknossos.datasets
             set isUsable = false, status = 'No longer available on datastore.', scale = NULL
             where _dataStore = ${dataStoreName}
             and #${inclusionPredicate}"""
    for {
      _ <- run(DBIO.sequence(List(deleteResolutionsQuery, deleteLayersQuery, setToUnusableQuery)).transactionally)
    } yield ()
  }

}


object DataSetResolutionsDAO extends SimpleSQLDAO {

  def parseRow(row: DatasetResolutionsRow): Fox[Point3D] = {
    for {
      resolution <- Point3D.fromList(parseArrayTuple(row.resolution).map(_.toInt)) ?~> "could not parse resolution"
    } yield resolution
  }

  def findDataResolutionForLayer(dataSetId: ObjectId, dataLayerName: String): Fox[List[Point3D]] = {
    for {
      rows <- run(DatasetResolutions.filter(r => r._Dataset === dataSetId.id && r.datalayername === dataLayerName).result).map(_.toList)
      rowsParsed <- Fox.combined(rows.map(parseRow)) ?~> "could not parse resolution row"
    } yield {
      rowsParsed
    }
  }

  def updateResolutions(_dataSet: ObjectId, dataLayersOpt: Option[List[DataLayer]]): Fox[Unit] = {
    val clearQuery = sqlu"delete from webknossos.dataSet_resolutions where _dataSet = ${_dataSet.id}"
    val insertQueries = dataLayersOpt match {
      case Some(dataLayers: List[DataLayer]) => {
        dataLayers.map { layer =>
          layer.resolutions.map { resolution => {
            sqlu"""insert into webknossos.dataSet_resolutions(_dataSet, dataLayerName, resolution)
                       values(${_dataSet.id}, ${layer.name}, '#${writeStructTuple(resolution.toList.map(_.toString))}')"""
          }
          }
        }.flatten
      }
      case _ => List()
    }
    for {
      _ <- run(DBIO.sequence(List(clearQuery) ++ insertQueries).transactionally)
    } yield ()
  }

}


object DataSetDataLayerDAO extends SimpleSQLDAO {

  def parseRow(row: DatasetLayersRow, dataSetId: ObjectId): Fox[DataLayer] = {
    val result: Fox[Fox[DataLayer]] = for {
      category <- Category.fromString(row.category).toFox ?~> "Could not parse Layer Category"
      boundingBox <- BoundingBox.fromSQL(parseArrayTuple(row.boundingbox).map(_.toInt)).toFox ?~> "Could not parse boundingbox"
      elementClass <- ElementClass.fromString(row.elementclass).toFox ?~> "Could not parse Layer ElementClass"
      resolutions <- DataSetResolutionsDAO.findDataResolutionForLayer(dataSetId, row.name) ?~> "Could not find resolution for layer"
    } yield {
      (row.largestsegmentid, row.mappings) match {
        case (Some(segmentId), Some(mappings)) =>
                                  Fox.successful(AbstractSegmentationLayer(
                                  row.name,
                                  category,
                                  boundingBox,
                                  resolutions.sortBy(_.maxDim),
                                  elementClass,
                                  segmentId,
                                  parseArrayTuple(mappings).toSet
                                ))
        case (None, None) => Fox.successful(AbstractDataLayer(
                                  row.name,
                                  category,
                                  boundingBox,
                                  resolutions.sortBy(_.maxDim),
                                  elementClass
                                ))
        case _ => Fox.failure("Could not match Dataset Layer")
      }
    }
    result.flatten
  }

  def findAllForDataSet(dataSetId: ObjectId)(implicit ctx: DBAccessContext): Fox[List[DataLayer]] = {
    for {
      rows <- run(DatasetLayers.filter(_._Dataset === dataSetId.id).result).map(_.toList)
      rowsParsed <- Fox.combined(rows.map(parseRow(_, dataSetId)))
    } yield {
      rowsParsed
    }
  }

  def findOneByNameForDataSet(dataLayerName: String, dataSetId: ObjectId)(implicit ctx: DBAccessContext): Fox[DataLayer] = {
    for {
      rows <- run(DatasetLayers.filter(_._Dataset === dataSetId.id).filter(_.name === dataLayerName).result).map(_.toList)
      firstRow <- rows.headOption.toFox ?~> ("Could not find data layer " + dataLayerName)
      parsed <- parseRow(firstRow, dataSetId)
    } yield {
      parsed
    }
  }

  def insertLayerQuery(_dataSet: ObjectId, layer: DataLayer) =
    layer match {
      case s: AbstractSegmentationLayer => {
        sqlu"""insert into webknossos.dataset_layers(_dataSet, name, category, elementClass, boundingBox, largestSegmentId, mappings)
                    values(${_dataSet.id}, ${s.name}, '#${s.category.toString}', '#${s.elementClass.toString}',
                     '#${writeStructTuple(s.boundingBox.toSql.map(_.toString))}', ${s.largestSegmentId}, '#${writeArrayTuple(s.mappings.map(sanitize(_)).toList)}')"""
      }
      case d: AbstractDataLayer => {
        sqlu"""insert into webknossos.dataset_layers(_dataSet, name, category, elementClass, boundingBox)
                    values(${_dataSet.id}, ${d.name}, '#${d.category.toString}', '#${d.elementClass.toString}',
                     '#${writeStructTuple(d.boundingBox.toSql.map(_.toString))}')"""
      }
      case _ => throw new Exception("DataLayer type mismatch")
    }

  def updateLayers(_dataSet: ObjectId, source: InboxDataSource)(implicit ctx: DBAccessContext): Fox[Unit] = {
    val clearQuery = sqlu"delete from webknossos.dataset_layers where _dataSet = (select _id from webknossos.dataSets where _id = ${_dataSet.id})"
    val insertQueries = source.toUsable match {
      case Some(usable) => usable.dataLayers.map(insertLayerQuery(_dataSet, _))
      case None => List()
    }
    for {
      _ <- run(DBIO.sequence(List(clearQuery) ++ insertQueries))
      _ <- DataSetResolutionsDAO.updateResolutions(_dataSet, source.toUsable.map(_.dataLayers))
    } yield ()
  }
}


object DataSetAllowedTeamsDAO extends SimpleSQLDAO {

  def findAllForDataSet(dataSetId: ObjectId)(implicit ctx: DBAccessContext): Fox[List[ObjectId]] = {
    val query = for {
      (allowedteam, team) <- DatasetAllowedteams.filter(_._Dataset === dataSetId.id) join Teams on (_._Team === _._Id)
    } yield team._Id

    run(query.result).flatMap(rows => Fox.serialCombined(rows.toList)(ObjectId.parse(_)))
  }

  def updateAllowedTeamsForDataSet(_id: ObjectId, allowedTeams: List[ObjectId])(implicit ctx: DBAccessContext): Fox[Unit] = {
    val clearQuery =
      sqlu"""delete from webknossos.dataSet_allowedTeams
                             where _dataSet = (
                               select _id from webknossos.dataSets where _id = ${_id}
                             )"""

    val insertQueries = allowedTeams.map(teamId =>
      sqlu"""insert into webknossos.dataSet_allowedTeams(_dataSet, _team)
                                                              values((select _id from webknossos.dataSets where _id = ${_id}),
                                                                     ${teamId.id})""")

    val composedQuery = DBIO.sequence(List(clearQuery) ++ insertQueries)
    for {
      _ <- run(composedQuery.transactionally.withTransactionIsolation(Serializable), retryCount = 50, retryIfErrorContains = List(transactionSerializationError))
    } yield ()
  }
}


object DataSetLastUsedTimesDAO extends SimpleSQLDAO {
  def findForDataSetAndUser(dataSetId: ObjectId, userId: ObjectId): Fox[Long] = {
    for {
      rList <- run(sql"select lastUsedTime from webknossos.dataSet_lastUsedTimes where _dataSet = ${dataSetId} and _user = ${userId}".as[java.sql.Timestamp])
      r <- rList.headOption.toFox
    } yield (r.getTime)
  }

  def updateForDataSetAndUser(dataSetId: ObjectId, userId: ObjectId): Fox[Unit] = {
    val clearQuery = sqlu"delete from webknossos.dataSet_lastUsedTimes where _dataSet = ${dataSetId} and _user = ${userId}"
    val insertQuery = sqlu"insert into webknossos.dataSet_lastUsedTimes(_dataSet, _user, lastUsedTime) values(${dataSetId}, ${userId}, NOW())"
    val composedQuery = DBIO.sequence(List(clearQuery, insertQuery))
    for {
      _ <- run(composedQuery.transactionally.withTransactionIsolation(Serializable), retryCount = 50, retryIfErrorContains = List(transactionSerializationError))
    } yield ()
  }
}
