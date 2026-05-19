import { useEffect } from "react";
import { bootstrapAuthSession } from "../api/authGateway";
import { backfillLocalBattleTruthToBackend } from "../../battle/runtime/local/state/battleResultSync";

export function AuthSessionBootstrap() {
  useEffect(() => {
    void bootstrapAuthSession();
    void backfillLocalBattleTruthToBackend();
  }, []);

  return null;
}
