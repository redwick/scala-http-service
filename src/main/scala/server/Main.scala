package server

import com.typesafe.config.ConfigFactory
import server.app.Envs
import server.cloud.CloudManager
import server.mail.MailManager
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, Routers}
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main{

  private val logger = LoggerFactory.getLogger("master")
  private val config = ConfigFactory.load()

  def main(args: Array[String]): Unit = {
    if (Envs.check()){
      try {
        Await.result(ActorSystem(Main(), "Main").whenTerminated, Duration.Inf)
      } catch {
        case e: Throwable =>
          logger.error(e.toString)
          main(Array.empty[String])
      }
    }
  }
  def apply(): Behavior[NotUsed] = {
    Behaviors.setup { context =>
      val cloud = context.spawn(Routers.pool(poolSize = 1) {
        Behaviors.supervise(CloudManager()).onFailure[Exception](SupervisorStrategy.restart)
      }, "cloud")
      val mail = context.spawn(Routers.pool(poolSize = 1) {
        Behaviors.supervise(MailManager()).onFailure[Exception](SupervisorStrategy.restart)
      }, "mail")
      HttpManager(context.system, mail, cloud)
      Behaviors.empty
    }
  }
}