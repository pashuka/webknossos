package models

import com.mongodb.casbah.commons.Imports._
import com.novus.salat._
import com.mongodb.casbah.MongoConnection
import play.Configuration

package object context {
  val db = {
    import com.mongodb.casbah.commons.Imports._
    import com.mongodb.casbah.MongoConnection
    import play.api.Play.current
    import play.api.Play
    val conf = Play.configuration

    val url = conf.getString("mongo.url").getOrElse("127.0.0.1")
    val port = conf.getInt("mongo.port").getOrElse(27017)
    
    MongoConnection(url, port)
  }

  private def createConnection(dbName: String) = {
    import com.mongodb.casbah.commons.Imports._
    import play.api.Play.current
    import play.api.Play
    val conf = Play.configuration

    val connection = db(dbName)
    for {
      dbuser <- conf.getString("mongo.user")
      dbpasswd <- conf.getString("mongo.password")
    } connection.authenticate(dbuser, dbpasswd)
    connection
  }

  object DB {
    import play.api.Play.current
    import play.api.Play

    lazy val connection = createConnection(
      Play.configuration.getString("mongo.dbname").getOrElse("salat-dao"))

  }

  object KnowledgeDB {
    import play.api.Play
    import play.api.Play.current

    lazy val connection =
      createConnection(
        Play.configuration.getString("mongo.knowledgedb.dbname").getOrElse("salat-dao"))
  }

  implicit val ctx = {
    import com.novus.salat._
    import play.api.Play
    import play.api.Play.current
    val c = new Context {
      val name = "play-salat-context"

      override val typeHintStrategy = StringTypeHintStrategy(when = TypeHintFrequency.Always,
        typeHint = "_typeHint")
    }
    c.registerClassLoader(Play.classloader)
    c
  }
}