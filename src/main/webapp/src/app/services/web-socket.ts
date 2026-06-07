export interface WebSocketConfig {
  reconnectIfNotNormalClose?: boolean;
}

export class AppWebSocket {
  private socket?: WebSocket;
  private manuallyClosed = false;
  private messageHandlers: Array<(msg: MessageEvent) => void> = [];
  private openHandlers: Array<(event: Event) => void> = [];
  private closeHandlers: Array<(event: CloseEvent) => void> = [];

  constructor(
    private url: string,
    protocols?: string | string[],
    private config: WebSocketConfig = {},
  ) {
    this.connect(protocols);
  }

  connect(protocols?: string | string[]) {
    if (
      this.socket &&
      (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    this.manuallyClosed = false;
    this.socket = protocols ? new WebSocket(this.url, protocols) : new WebSocket(this.url);
    this.socket.onmessage = (event) => this.messageHandlers.forEach((handler) => handler(event));
    this.socket.onopen = (event) => this.openHandlers.forEach((handler) => handler(event));
    this.socket.onclose = (event) => {
      this.closeHandlers.forEach((handler) => handler(event));
      if (this.config.reconnectIfNotNormalClose && !this.manuallyClosed && event.code !== 1000) {
        window.setTimeout(() => this.connect(protocols), 1000);
      }
    };
  }

  close(force?: boolean) {
    this.manuallyClosed = true;
    this.socket?.close(force ? 1000 : undefined);
  }

  onMessage(handler: (msg: MessageEvent) => void, _options?: { autoApply?: boolean }) {
    this.messageHandlers.push(handler);
  }

  onOpen(handler: (event: Event) => void) {
    this.openHandlers.push(handler);
  }

  onClose(handler: (event: CloseEvent) => void) {
    this.closeHandlers.push(handler);
  }
}
