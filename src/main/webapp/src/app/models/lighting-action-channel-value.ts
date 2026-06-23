export class LightingActionChannelValue {
  channel: number;
  value: number;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.channel = data.channel;
    this.value = data.value;
  }
}
