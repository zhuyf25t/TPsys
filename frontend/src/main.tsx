import ReactDOM from "react-dom/client";
import { App } from "./app/App";
import { AppProviders } from "./app/providers/AppProviders";
import { sanitizeStartupStorage } from "./app/storage/startupStorageSanitizer";
import "./app/tailwind.css";

const appElement = document.getElementById("app");

if (!appElement) {
  throw new Error("Missing #app root element.");
}

sanitizeStartupStorage();

ReactDOM.createRoot(appElement).render(
  <AppProviders>
    <App />
  </AppProviders>
);
