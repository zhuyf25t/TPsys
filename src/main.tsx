import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./app/App";
import { AppErrorBoundary } from "./shared/ui/AppErrorBoundary";
import "./app/styles.css";

const appElement = document.getElementById("app");

if (!appElement) {
  throw new Error("Missing #app root element.");
}

ReactDOM.createRoot(appElement).render(
  <AppErrorBoundary>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </AppErrorBoundary>
);
