package server

import com.typesafe.config.ConfigFactory
import io.circe.syntax.EncoderOps
import server.app.Codes
import server.cloud.CloudManager._
import server.http.AppMessage._
import org.apache.pekko.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.http.cors.scaladsl.CorsDirectives._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.Timeout
import org.slf4j.LoggerFactory
import java.util.{Calendar, TimeZone}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, MINUTES, SECONDS}

object HttpManager extends Codes with DBLogger {

  private val logger = LoggerFactory.getLogger("server/http")
  private val config = ConfigFactory.load()


  def apply(system: ActorSystem[Nothing],
            mail: ActorRef[MailManagerMessage],
            cloud: ActorRef[(CloudManagerMessage, AppSender)],
           ): Future[Http.ServerBinding] = {
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
      implicit val sys: ActorSystem[Nothing] = system
      implicit val timeout: Timeout = Duration(5, SECONDS)
      val route: Route = cors() {
        concat(
          basicRoutes(),
          cloudManagerRoutes(system, cloud, mail),
        )
      }
      logger.info("http started at " + config.getString("http.host") + ":" + config.getString("http.port"))
      Http().newServerAt(config.getString("http.host"), config.getInt("http.port")).bind(route)
    }
    catch {
      case e: Throwable =>
        println(e.toString)
        Thread.sleep(5 * 1000)
        HttpManager(system, mail, cloud)
    }
  }



  def forward[A <: AppRequestMessage](system: ActorSystem[_], actorRef: ActorRef[(A, AppSender)], message: A, mail: ActorRef[MailManagerMessage]): Route = {
    try {
      implicit val sys: ActorSystem[Nothing] = system
      implicit val timeout: Timeout = Duration(1, MINUTES)
      val date = Calendar.getInstance().getTime.getTime
      (extractHost & extractClientIP){ (hn, ip) =>
        complete(parseResponse(actorRef.ask((replyTo: ActorRef[AppResponseMessage]) => {
          (message, AppSender(replyTo, mail, date))
        })))
      }
    }
    catch {
      case e: Throwable =>
        saveErrorLog(e.toString, cmd = message.toString)
        complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(e.toString.asJson.noSpaces)))
    }
  }

  private def basicRoutes(): Route = {
    concat(
      (get & path("time")) {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Calendar.getInstance().getTime.toString.asJson.noSpaces)))
      },
    )
  }
  private def cloudManagerRoutes(system: ActorSystem[_], actor: ActorRef[(CloudManagerMessage, AppSender)], mail: ActorRef[MailManagerMessage]): Route = {
    concat(
      (get & path("cloud-files") & parameter("path")) { (path) =>
        forward(system, actor, GetCloudFiles(path), mail)
      },
    )
  }


  private def parseResponse(ask: Future[AppResponseMessage]): Future[HttpResponse] = {
    ask.flatMap {
      case SuccessTextResponse(text) => Future.successful(
        HttpResponse(StatusCodes.OK, entity = HttpEntity(text))
      )
      case NotAllowedTextResponse(text) => Future.successful(
        HttpResponse(StatusCodes.MethodNotAllowed, entity = HttpEntity(text))
      )
      case BadRequestTextResponse(text) => Future.successful(
        HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(text))
      )
      case ErrorTextResponse(text) => Future.successful(
        HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(text))
      )
      case _ => Future.successful(
        HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(TextCodesRu.ServerError))
      )
    }
  }

}
