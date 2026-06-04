import type { BattleCommandAPIMessageRequest } from "../../../../../objects/battle/microservices/session/api/command/BattleCommandRequestApiTypes";
import type { BattleStateReadAPIMessageRequest } from "../../../../../objects/battle/microservices/session/api/state/BattleStateApiTypes";
import {
  BATTLE_API_MESSAGES,
  postBattleApiMessage,
  type BattleApiMessageDecoder,
  type BattleApiMessageOptions,
  type BattleApiMessageResponse
} from "../../../BattleApiMessageTransport";

export type { BattleCommandAPIMessageRequest } from "../../../../../objects/battle/microservices/session/api/command/BattleCommandRequestApiTypes";

export type { BattleStateReadAPIMessageRequest } from "../../../../../objects/battle/microservices/session/api/state/BattleStateApiTypes";

export function postBattleStateReadAPIMessage<TPayload>(
  request: BattleStateReadAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.stateRead, request, decoder, options);
}

export function postBattleCommandAPIMessage<TPayload>(
  request: BattleCommandAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.command, request, decoder, options);
}
