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
    socket.addEventListener("open", callbacks.opened);
    socket.addEventListener("message", (event) => {
      if (typeof event.data === "string") {
        callbacks.received(event.data);
      } else {
        callbacks.received("");
      }
    });
    socket.addEventListener("close", callbacks.closed);
    socket.addEventListener("error", () => socket.close());
    return { close: () => socket.close() };
  }
}
