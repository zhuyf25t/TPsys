package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Method, Request, Uri}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*
import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}

object BattleQueueHttp4sJoinContractTest {
  private val ValidJoinJson: String =
    """{"handle":"alice","sessionToken":"session-alice","queueRequestId":"join-route-1","rating":1200,"avatar":"blue","skin":"pilot"}"""

  def main(args: Array[String]): Unit = {
    validJoinAuthorizesAndReturnsSnapshot()
    restJoinPathMatchesCurrentHttpApi()
    frontendProxyLegacyJoinPathIsSupported()
    invalidHandleIsRejectedBeforeAuthorization()
    missingSessionIsUnauthorizedBeforeQueue()
    invalidRatingIsBadRequest()
    invalidSessionIsUnauthorized()
    handleMismatchIsForbidden()

    println("Battle queue http4s join contract checks passed")
  }

  private def frontendProxyLegacyJoinPathIsSupported(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))
    val response = postJson(queueService, authService, uri"/battlequeuejoinapi", ValidJoinJson)

    assertEquals("frontend proxy legacy join status", response.status, 200)
    assertContains("frontend proxy legacy join ticket", response.body, """"ticketId":"ticket-alice"""")
    assertEquals("frontend proxy legacy join queue count", queueService.commands.length, 1)
  }

  private def validJoinAuthorizesAndReturnsSnapshot(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))
    val response = postJson(queueService, authService, uri"/api/battlequeuejoinapi", ValidJoinJson)

    assertEquals("valid join status", response.status, 200)
    assertContains("valid join ticket", response.body, """"ticketId":"ticket-alice"""")
    assertContains("valid join participant rating", response.body, """"rating":1200""")
    assertContains("valid join participant avatar", response.body, """"avatar":"blue"""")
    assertContains("valid join participant skin", response.body, """"skin":"pilot"""")
    assertEquals("valid join auth count", authService.commands.length, 1)
    assertEquals("valid join queue count", queueService.commands.length, 1)
    val command = queueService.commands.head
    assertEquals("valid join handle", command.handle, PlayerHandle("alice"))
    assertEquals("valid join session", command.sessionToken, SessionToken("session-alice"))
    assertEquals("valid join request id", command.queueRequestId, Some(QueueRequestId("join-route-1")))
    assertEquals("valid join rating", command.rating, Some(Rating(1200)))
    assertEquals("valid join avatar", command.avatar, Some("blue"))
    assertEquals("valid join skin", command.skin, Some("pilot"))
  }

  private def restJoinPathMatchesCurrentHttpApi(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))
    val response = postJson(queueService, authService, uri"/api/battle/queue/join", ValidJoinJson)

    assertEquals("rest join path status", response.status, 200)
    assertContains("rest join path ticket", response.body, """"ticketId":"ticket-alice"""")
    assertEquals("rest join path queue count", queueService.commands.length, 1)
  }

  private def invalidHandleIsRejectedBeforeAuthorization(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))
    val response = postJson(
      queueService,
      authService,
      uri"/api/battlequeuejoinapi",
      ValidJoinJson.replace("\"handle\":\"alice\"", "\"handle\":\"visitor\"")
    )

    assertEquals("invalid handle status", response.status, 400)
    assertContains("invalid handle code", response.body, """"code":"invalid_handle"""")
    assertEquals("invalid handle auth count", authService.commands.length, 0)
    assertEquals("invalid handle queue count", queueService.commands.length, 0)
  }

  private def missingSessionIsUnauthorizedBeforeQueue(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))
    val response = postJson(
      queueService,
      authService,
      uri"/api/battlequeuejoinapi",
      ValidJoinJson.replace("\"sessionToken\":\"session-alice\",", "")
    )

    assertEquals("missing session status", response.status, 401)
    assertContains("missing session code", response.body, """"code":"missing_session"""")
    assertEquals("missing session auth count", authService.commands.length, 0)
    assertEquals("missing session queue count", queueService.commands.length, 0)
  }

  private def invalidRatingIsBadRequest(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))
    val response = postJson(
      queueService,
      authService,
      uri"/api/battlequeuejoinapi",
      ValidJoinJson.replace("\"rating\":1200", "\"rating\":\"bad\"")
    )

    assertEquals("invalid rating status", response.status, 400)
    assertContains("invalid rating code", response.body, """"code":"bad_request"""")
    assertContains("invalid rating message", response.body, """"error":"rating must be an integer."""")
    assertEquals("invalid rating auth count", authService.commands.length, 0)
    assertEquals("invalid rating queue count", queueService.commands.length, 0)
  }

  private def invalidSessionIsUnauthorized(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Left(BattleQueueJoinAuthorizationError.InvalidSession))
    val response = postJson(queueService, authService, uri"/api/battlequeuejoinapi", ValidJoinJson)

    assertEquals("invalid session status", response.status, 401)
    assertContains("invalid session code", response.body, """"code":"invalid_session"""")
    assertEquals("invalid session auth count", authService.commands.length, 1)
    assertEquals("invalid session queue count", queueService.commands.length, 0)
  }

  private def handleMismatchIsForbidden(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Left(BattleQueueJoinAuthorizationError.HandleMismatch))
    val response = postJson(queueService, authService, uri"/api/battlequeuejoinapi", ValidJoinJson)

    assertEquals("handle mismatch status", response.status, 403)
    assertContains("handle mismatch code", response.body, """"code":"identity_mismatch"""")
    assertEquals("handle mismatch auth count", authService.commands.length, 1)
    assertEquals("handle mismatch queue count", queueService.commands.length, 0)
  }

  private def postJson(
    queueService: RecordingBattleQueueService,
    authService: RecordingJoinAuthorizationService,
    targetUri: Uri,
    body: String
  ): RouteResponse = {
    val request = Request[IO](method = Method.POST, uri = targetUri)
      .withEntity(body)
      .putHeaders(`Content-Type`(MediaType.application.json))
    val response = BackendHttp4sRoutes
      .battleQueueJoinRoutes(queueService, authService)
      .orNotFound
      .run(request)
      .unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingBattleQueueService extends BattleQueueService {
    private var recordedCommands: Vector[BattleQueueJoinCommand] = Vector.empty

    def commands: Vector[BattleQueueJoinCommand] =
      recordedCommands

    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot = {
      recordedCommands = recordedCommands :+ command
      BattleQueueSnapshot(
        ticketId = TicketId("ticket-alice"),
        playerId = PlayerId("alice"),
        roomId = RoomId("room-alice"),
        createdAt = EpochMillis(1_000L),
        startsAt = EpochMillis(6_000L),
        deadline = EpochMillis(9_000L),
        serverTime = EpochMillis(1_000L),
        participants = Vector(
          BattleQueueParticipant(
            playerId = PlayerId("alice"),
            handle = PlayerHandle("alice"),
            joinedAt = EpochMillis(1_000L),
            lastSeen = EpochMillis(1_000L),
            rating = Some(Rating(1200)),
            avatar = Some("blue"),
            skin = Some("pilot")
          )
        ),
        capacity = BattleCapacity(2),
        durationMs = DurationMillis(300_000L),
        phase = MatchmakingRoomPhase.Waiting,
        finishedAt = None,
        battleSession = None
      )
    }

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] =
      failUnused()

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
      failUnused()

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] =
      failUnused()

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] =
      failUnused()

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      failUnused()

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      failUnused()
  }

  private object RecordingBattleQueueService {
    def apply(): RecordingBattleQueueService =
      new RecordingBattleQueueService()
  }

  private final class RecordingJoinAuthorizationService(
    result: Either[BattleQueueJoinAuthorizationError, Unit]
  ) extends BattleQueueJoinAuthorizationService {
    private var recordedCommands: Vector[BattleQueueJoinCommand] = Vector.empty

    def commands: Vector[BattleQueueJoinCommand] =
      recordedCommands

    override def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit] = {
      recordedCommands = recordedCommands :+ command
      result
    }
  }

  private object RecordingJoinAuthorizationService {
    def apply(result: Either[BattleQueueJoinAuthorizationError, Unit]): RecordingJoinAuthorizationService =
      new RecordingJoinAuthorizationService(result)
  }

  private def failUnused[A](): A =
    throw AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, text: String, expected: String): Unit =
    assert(text.contains(expected), s"$label: expected response to contain $expected, got $text")
}
