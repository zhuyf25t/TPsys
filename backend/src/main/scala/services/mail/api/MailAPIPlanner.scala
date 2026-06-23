package services.mail.api

import cats.effect.IO

import services.mail.services.MailService

object MailAPIPlanner {
  def planList(service: MailService, message: MailListAPIMessage): IO[MailListResponse] =
    for
      owner <- IO.fromEither(
        MailOwnerQuery.parse(message.ownerHandle).left.map(MailAPIMessageErrors.owner)
      )
      records <- service.list(owner)
    yield MailListResponse.fromRecords(records)

  def planRead(service: MailService, message: MailReadAPIMessage): IO[MailReadResponse] =
    for
      command <- IO.fromEither(
        MailCommandParsers.parseReadCommand(message).left.map(MailAPIMessageErrors.readRoute)
      )
      response <- service.markRead(command.ownerHandle, command.mailId).flatMap(MailAPIMessageErrors.markRead)
    yield response
}
