import { useEffect } from "react";
import { bootstrapAuthSession } from "../../api/identity/authGateway";
import { backfillLocalBattleTruthToBackend } from "../../runtime/battle/local/state/battleTruthStore";

/** 中文名：auth会话bootstrap（AuthSessionBootstrap）。游戏职责：在前端身份域中组织玩家名、登录态和资料展示，统一玩家身份入口。 */
export function AuthSessionBootstrap() {
  useEffect(() => {
    void bootstrapAuthSession();
    void backfillLocalBattleTruthToBackend();
  }, []);

  return null;
}
