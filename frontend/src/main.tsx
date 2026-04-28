import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./app/App";
import { AppErrorBoundary } from "./shared/ui/AppErrorBoundary";
import { sanitizeStartupStorage } from "./shared/storage/startupStorageSanitizer";
import "./app/styles.css";

const appElement = document.getElementById("app");

if (!appElement) {
  throw new Error("Missing #app root element.");
}

sanitizeStartupStorage();

ReactDOM.createRoot(appElement).render(
  <AppErrorBoundary>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </AppErrorBoundary>
);
