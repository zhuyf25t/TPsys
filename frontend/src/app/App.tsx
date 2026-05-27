import { AuthSessionBootstrap } from "../components/auth/AuthSessionBootstrap";
import { AppRoutes } from "./routes";

export function App() {
  return (
    <>
      <AuthSessionBootstrap />
      <AppRoutes />
    </>
  );
}
