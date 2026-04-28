import { useEffect } from "react";
import { bootstrapAuthSession } from "../../features/auth/authGateway";
import { backfillLocalBattleTruthToBackend } from "../../features/battle/local/battleResultSync";

export function AuthSessionBootstrap() {
  useEffect(() => {
    void bootstrapAuthSession();
    void backfillLocalBattleTruthToBackend();
  }, []);

  return null;
}
