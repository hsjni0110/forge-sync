import type {
  TwinSocket,
  TwinSocketCallbacks,
  TwinSocketFactory,
} from "../application/ports";

export class BrowserTwinSocketFactory implements TwinSocketFactory {
  constructor(private readonly apiBaseUrl = window.location.origin) {}

  connect(machineId: string, callbacks: TwinSocketCallbacks): TwinSocket {
    const url = new URL(this.apiBaseUrl || window.location.origin);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    url.pathname = `/api/v1/ws/machines/${encodeURIComponent(machineId)}/twin`;
    const socket = new WebSocket(url);
    let hasReportedClosed = false;
    const reportClosed = () => {
      window.removeEventListener("offline", closeForOfflineNetwork);
      if (!hasReportedClosed) {
        hasReportedClosed = true;
        callbacks.closed();
      }
    };
    const closeForOfflineNetwork = () => {
      socket.close();
      reportClosed();
    };
    socket.addEventListener("open", callbacks.opened);
    socket.addEventListener("message", (event) => {
      if (typeof event.data === "string") {
        callbacks.received(event.data);
      } else {
        callbacks.received("");
      }
    });
    socket.addEventListener("close", reportClosed);
    socket.addEventListener("error", () => socket.close());
    window.addEventListener("offline", closeForOfflineNetwork);
    return {
      close: () => {
        window.removeEventListener("offline", closeForOfflineNetwork);
        socket.close();
      },
    };
  }
}
