package server.cloud.controls

import io.circe.syntax.EncoderOps
import server.cloud.tables.CloudFilesTable
import server.http.AppMessage.{AppSender, CloudManagerMessage, SuccessTextResponse}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.LoggerFactory

trait CloudFilesControl extends CloudFilesTable {

  private val logger = LoggerFactory.getLogger(this.toString)

  case class GetCloudFiles(path: String) extends CloudManagerMessage

  def cloudFilesControl: PartialFunction[(CloudManagerMessage, AppSender), Behavior[(CloudManagerMessage, AppSender)]] = {
    case (GetCloudFiles(path), sender) =>
      sender.ref.tell(SuccessTextResponse(
        getCloudFiles(path).sortBy(_.file_name)
        .asJson.noSpaces))
      Behaviors.same
    case (_, _) =>
      Behaviors.same
  }
}
