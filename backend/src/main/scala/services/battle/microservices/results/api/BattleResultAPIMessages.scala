package services.battle.microservices.results.api

import services.battle.microservices.results.api.results.{
  BattleResultListResponse,
  BattleResultRecordResponse
}
import system.api.RegisteredAPIMessage
import system.api.RegisteredAPIMessage.apiWithToken

object BattleResultAPIMessages {
  import BattleResultListResponse.given
  import BattleResultRecordResponse.given

  val connectionBackedMessages: List[RegisteredAPIMessage] =
    List(
      apiWithToken[
        BattleResultListAPIMessage,
        BattleResultListResponse
      ],
      apiWithToken[
        BattleResultRecordAPIMessage,
        BattleResultRecordResponse
      ]
    )
}
