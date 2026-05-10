package slaydemo.backend.battle.routes

import java.io.{IOException, OutputStream}
import java.nio.charset.StandardCharsets

import slaydemo.backend.battle.objects.{BattleAggregateState, BattleId, BattlePhase}

private[routes] object BattleStateStreamWriter {
  private val StateStreamSleepMs: Long = 33L

  def writeStateFrames(
    output: OutputStream,
    battleId: BattleId,
    initialState: BattleAggregateState,
    nextState: BattleId => Option[BattleAggregateState]
  ): Unit =
    try {
      writeFrames(output, battleId, initialState, nextState)
    } catch {
      case _: IOException =>
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
    } finally {
      output.close()
    }

  private def writeFrames(
    output: OutputStream,
    battleId: BattleId,
    initialState: BattleAggregateState,
    nextState: BattleId => Option[BattleAggregateState]
  ): Unit = {
    var currentState = Option(initialState)
    var streaming = true

    while streaming do {
      currentState match {
        case None =>
          streaming = false
        case Some(state) =>
          val frame = s"event: state\ndata: ${BattleStateJson.renderState(state)}\n\n"
          output.write(frame.getBytes(StandardCharsets.UTF_8))
          output.flush()

          if state.phase == BattlePhase.Finished then streaming = false
          else {
            Thread.sleep(StateStreamSleepMs)
            currentState = nextState(battleId)
          }
      }
    }
  }
}
