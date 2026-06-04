import type {
  BattleResultListAPIMessageRequest
} from "../../../../../objects/battle/microservices/results/api/results/BattleResultListRequestApiTypes";
import type {
  BattleResultRecordAPIMessageRequest
} from "../../../../../objects/battle/microservices/results/api/results/BattleResultRecordRequestApiTypes";
import {
  BATTLE_API_MESSAGES,
  postBattleApiMessage,
  type BattleApiMessageDecoder,
  type BattleApiMessageOptions,
  type BattleApiMessageResponse
} from "../../../BattleApiMessageTransport";

export type {
  BattleResultListAPIMessageRequest
} from "../../../../../objects/battle/microservices/results/api/results/BattleResultListRequestApiTypes";

export type {
  BattleResultRecordAPIMessageRequest
} from "../../../../../objects/battle/microservices/results/api/results/BattleResultRecordRequestApiTypes";

export function postBattleResultListAPIMessage<TPayload>(
  request: BattleResultListAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.resultList, request, decoder, options);
}

export function postBattleResultRecordAPIMessage<TPayload>(
  request: BattleResultRecordAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.resultRecord, request, decoder, options);
}
