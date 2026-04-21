import { useEffect } from "react";
import { bootstrapAuthSession } from "../../features/auth/authGateway";

export function AuthSessionBootstrap() {
  useEffect(() => {
    void bootstrapAuthSession();
  }, []);

  return null;
}
