package server.http

import org.apache.pekko.actor.typed.ActorRef



object AppMessage {

  trait AppRequestMessage
  trait AppResponseMessage

  case class AppSender(ref: ActorRef[AppResponseMessage], mail: ActorRef[MailManagerMessage], date: Long) extends AppRequestMessage

  case class SuccessTextResponse(text: String) extends AppResponseMessage
  case class ErrorTextResponse(text: String) extends AppResponseMessage
  case class NotAllowedTextResponse(text: String) extends AppResponseMessage
  case class BadRequestTextResponse(text: String) extends AppResponseMessage

  trait CloudManagerMessage extends AppRequestMessage
  trait MailManagerMessage extends AppRequestMessage

}
