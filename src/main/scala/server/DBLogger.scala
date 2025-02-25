package server

import server.DBManager.PostgresSQL
import server.HttpManager.TextCodes
import org.apache.pekko.http.scaladsl.model.RemoteAddress
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}

import java.util.Date
import scala.concurrent.Future

trait DBLogger {

  case class AccessLog(id: Int, user: String, ip: String, cmd: String, date: Long)

  private val logger = LoggerFactory.getLogger(this.toString)

  object LogKinds {
    val Access = "access"
    val Error = "error"
    val Info = "info"
    val Warning = "warning"
  }


  case class CommandLog(id: Int, kind: String, user: String, ip: String, host: String, cmd: String, text: String, date: Long)
  case class CommandLogable(tag: Tag) extends Table[CommandLog](tag, "logs") {
    val id = column[Int]("id", O.AutoInc, O.PrimaryKey)
    val kind = column[String]("kind")
    val user = column[String]("user")
    val ip = column[String]("ip")
    val host = column[String]("host")
    val cmd = column[String]("cmd")
    val text = column[String]("text")
    val date = column[Long]("date")
    override def * = (id, kind, user, ip, host, cmd, text, date) <> ((CommandLog.apply _).tupled, CommandLog.unapply)
  }
  private val logTable = TableQuery[CommandLogable]

  PostgresSQL.run(DBIO.seq(
    logTable.schema.createIfNotExists,
  ))
  def saveInfoLog(text: String, hn: String, cmd: String = TextCodes.CodeError): Unit = {
    try{
      val d = new Date().getTime
      addLog(CommandLog(0, LogKinds.Info, TextCodes.Undefined, TextCodes.Undefined, hn, cmd, text, d))
    }
    catch {
      case e: Throwable => logger.error(e.toString)
    }
  }
  def saveErrorLog(text: String, u: String = TextCodes.Undefined, cmd: String = TextCodes.Undefined): Unit = {
    try{
      val d = new Date().getTime
      addLog(CommandLog(0, LogKinds.Error, u, TextCodes.Undefined, TextCodes.Undefined, cmd, text, d))
    }
    catch {
      case e: Throwable => logger.error(e.toString)
    }
  }
  def saveAccessLog(ip: RemoteAddress, text: String, cmd: String = TextCodes.Undefined, hn: String = TextCodes.Undefined, u: String = TextCodes.Undefined): Unit = {
    try{
      val ipAddr = ip.toOption.map(_.getHostAddress).getOrElse("unknown")
      val d = new Date().getTime
      addLog(CommandLog(0, LogKinds.Access, u, ipAddr, hn, cmd, text, d))
    }
    catch {
      case e: Throwable => logger.error(e.toString)
    }
  }
  def addLog(value: CommandLog): Future[Int] = {
    PostgresSQL.run(logTable.insertOrUpdate(value))
  }

}
