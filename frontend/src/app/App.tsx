import { AuthSessionBootstrap } from "../domains/identity/components/AuthSessionBootstrap";
import { AppRoutes } from "./routes";

export function App() {
  return (
    <>
      <AuthSessionBootstrap />
      <AppRoutes />
    </>
  );
}
