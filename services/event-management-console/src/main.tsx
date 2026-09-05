import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./app/App";
import "./shared/theme/tokens.css";
import "./shared/theme/global.css";

ReactDOM.createRoot(document.getElementById("root")!).render(<React.StrictMode><App /></React.StrictMode>);
