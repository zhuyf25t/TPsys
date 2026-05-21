package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Method, Request, Uri}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*
import slaydemo.backend.identity.objects.PlayerHandle

object BattleRoomHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    roomSnapshotReadsQueryRoomId()
    roomSnapshotReadsPathRoomId()
    restRoomSnapshotAndHeartbeatUseQueryRoomId()
    roomHeartbeatCombinesQueryAndBody()
    roomHeartbeatReadsPathRoomId()
    missingRoomIdIsBadRequest()
    heartbeatNonObjectBodyIsBadRequest()
    roomNotFoundIsNotFound()

    println("Battle room http4s contract checks passed")
  }

  private def roomSnapshotReadsQueryRoomId(): Unit = {
    val service = RecordingBattleQueueService()
    val response = runSnapshot(service, Request[IO](method = Method.GET, uri = uri"/api/battleroomsnapshotapi?roomId=room-route"))

    assertEquals("snapshot status", response.status, 200)
    assertContains("snapshot room id", response.body, """"roomId":"room-route"""")
    assertContains("snapshot phase", response.body, """"phase":"waiting"""")
    assertEquals("snapshot calls", service.roomSnapshotCalls, Vector(RoomId("room-route")))
  }

  private def roomSnapshotReadsPathRoomId(): Unit = {
    val service = RecordingBattleQueueService()
    val response = runSnapshot(service, Request[IO](method = Method.GET, uri = uri"/api/battle/rooms/path-room/snapshot"))

    assertEquals("path snapshot status", response.status, 200)
    assertContains("path snapshot room id", response.body, """"roomId":"path-room"""")
    assertEquals("path snapshot calls", service.roomSnapshotCalls, Vector(RoomId("path-room")))
  }

  private def restRoomSnapshotAndHeartbeatUseQueryRoomId(): Unit = {
    val service = RecordingBattleQueueService()
    val snapshot = runSnapshot(service, Request[IO](method = Method.GET, uri = uri"/api/battle/rooms/snapshot?roomId=room-route"))
    val heartbeat = postHeartbeat(
      service,
      uri"/api/battle/rooms/heartbeat?roomId=room-route",
      """{"ticketId":"ticket-route","handle":"Alice"}"""
    )

    assertEquals("rest room snapshot status", snapshot.status, 200)
    assertContains("rest room snapshot id", snapshot.body, """"roomId":"room-route"""")
    assertEquals("rest room snapshot calls", service.roomSnapshotCalls, Vector(RoomId("room-route")))
    assertEquals("rest heartbeat status", heartbeat.status, 200)
    assertContains("rest heartbeat room id", heartbeat.body, """"roomId":"room-route"""")
    assertEquals("rest heartbeat call count", service.heartbeatCalls.length, 1)
    val command = service.heartbeatCalls.head
    assertEquals("rest heartbeat query room id", command.roomId, Some(RoomId("room-route")))
    assertEquals("rest heartbeat ticket id", command.ticketId, Some(TicketId("ticket-route")))
    assertEquals("rest heartbeat handle", command.handle, Some(PlayerHandle("Alice")))
  }

  private def roomHeartbeatCombinesQueryAndBody(): Unit = {
    val service = RecordingBattleQueueService()
    val response = postHeartbeat(
      service,
      uri"/api/battleroomheartbeatapi?roomId=room-route",
      """{"ticketId":"ticket-route","handle":"Alice"}"""
    )

    assertEquals("heartbeat status", response.status, 200)
    assertContains("heartbeat room id", response.body, """"roomId":"room-route"""")
    assertEquals("heartbeat call count", service.heartbeatCalls.length, 1)
    val command = service.heartbeatCalls.head
    assertEquals("heartbeat query room id", command.roomId, Some(RoomId("room-route")))
    assertEquals("heartbeat body ticket id", command.ticketId, Some(TicketId("ticket-route")))
    assertEquals("heartbeat body handle", command.handle, Some(PlayerHandle("Alice")))
  }

  private def roomHeartbeatReadsPathRoomId(): Unit = {
    val service = RecordingBattleQueueService()
    val response = postHeartbeat(
      service,
      uri"/api/battle/rooms/path-room/heartbeat?ticketId=ticket-route&handle=Alice",
      "{}"
    )

    assertEquals("path heartbeat status", response.status, 200)
    assertContains("path heartbeat room id", response.body, """"roomId":"path-room"""")
    val command = service.heartbeatCalls.head
    assertEquals("path heartbeat room id", command.roomId, Some(RoomId("path-room")))
    assertEquals("path heartbeat ticket id", command.ticketId, Some(TicketId("ticket-route")))
    assertEquals("path heartbeat handle", command.handle, Some(PlayerHandle("Alice")))
  }

  private def missingRoomIdIsBadRequest(): Unit = {
    val service = RecordingBattleQueueService()
    val snapshot = runSnapshot(service, Request[IO](method = Method.GET, uri = uri"/api/battleroomsnapshotapi"))
    val heartbeat = postHeartbeat(service, uri"/api/battleroomheartbeatapi", "{}")

    assertEquals("missing snapshot status", snapshot.status, 400)
    assertEquals("missing snapshot body", snapshot.body, """{"error":"roomId is required.","code":"invalid_room_id"}""")
    assertEquals("missing heartbeat status", heartbeat.status, 400)
    assertEquals("missing heartbeat body", heartbeat.body, """{"error":"roomId is required.","code":"invalid_room_id"}""")
  }

  private def heartbeatNonObjectBodyIsBadRequest(): Unit = {
    val service = RecordingBattleQueueService()
    val response = postHeartbeat(service, uri"/api/battleroomheartbeatapi?roomId=room-route", "[]")

    assertEquals("non-object heartbeat status", response.status, 400)
    assertEquals(
      "non-object heartbeat body",
      response.body,
      """{"error":"Request body must be a JSON object with supported primitive or object fields.","code":"bad_request"}"""
    )
    assertEquals("non-object heartbeat no service call", service.heartbeatCalls, Vector.empty)
  }

  private def roomNotFoundIsNotFound(): Unit = {
    val service = RecordingBattleQueueService(roomResult = Left(BattleRoomError.RoomNotFound))
    val response = runSnapshot(service, Request[IO](method = Method.GET, uri = uri"/api/battleroomsnapshotapi?roomId=missing"))

    assertEquals("room not found status", response.status, 404)
    assertEquals("room not found body", response.body, """{"error":"Battle room was not found.","code":"room_not_found"}""")
  }

  private def runSnapshot(service: RecordingBattleQueueService, request: Request[IO]): RouteResponse = {
    val response = BackendHttp4sRoutes.battleRoomSnapshotRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private def postHeartbeat(service: RecordingBattleQueueService, targetUri: Uri, body: String): RouteResponse = {
    val request = Request[IO](method = Method.POST, uri = targetUri)
      .withEntity(body)
      .putHeaders(`Content-Type`(MediaType.application.json))
    val response = BackendHttp4sRoutes.battleRoomHeartbeatRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingBattleQueueService(
    roomResult: Either[BattleRoomError, RealtimeRoomSnapshot] = Right(roomSnapshotFor(RoomId("room-route")))
  ) extends BattleQueueService {
    var roomSnapshotCalls: Vector[RoomId] = Vector.empty
    var heartbeatCalls: Vector[RealtimeRoomHeartbeatCommand] = Vector.empty

    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      failUnused()

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] =
      failUnused()

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
      failUnused()

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] = {
      roomSnapshotCalls = roomSnapshotCalls :+ roomId
      roomResult.map(_.copy(roomId = roomId))
    }

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] = {
      heartbeatCalls = heartbeatCalls :+ request
      request.roomId match {
        case None =>
          Left(BattleRoomError.MissingRoomId)
        case Some(roomId) =>
          roomResult.map(_.copy(roomId = roomId))
      }
    }

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      None

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      ()
  }

  private object RecordingBattleQueueService {
    def apply(roomResult: Either[BattleRoomError, RealtimeRoomSnapshot] = Right(roomSnapshotFor(RoomId("room-route")))): RecordingBattleQueueService =
      new RecordingBattleQueueService(roomResult)
  }

  private def roomSnapshotFor(roomId: RoomId): RealtimeRoomSnapshot =
    RealtimeRoomSnapshot(
      roomId = roomId,
      serverTime = EpochMillis(1_500L),
      participants = Vector(
        BattleQueueParticipant(
          playerId = PlayerId("player-route"),
          handle = PlayerHandle("Alice"),
          joinedAt = EpochMillis(1_000L),
          lastSeen = EpochMillis(1_500L),
          rating = None,
          avatar = None,
          skin = None
        )
      ),
      capacity = BattleCapacity(2),
      phase = MatchmakingRoomPhase.Waiting,
      finishedAt = None,
      battleSession = None
    )

  private def failUnused[A](): A =
    throw AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
