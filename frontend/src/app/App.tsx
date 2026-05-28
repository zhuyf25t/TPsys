import { AuthSessionBootstrap } from "./providers/AuthSessionBootstrap";
import { AppRoutes } from "./routes";

export function App() {
  return (
    <>
      <AuthSessionBootstrap />
      <AppRoutes />
    </>
  );
}
