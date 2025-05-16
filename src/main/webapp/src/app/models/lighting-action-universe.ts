import { LightingActionChannelValue } from "./lighting-action-channel-value";

export class LightingActionUniverse {
  universeName: string;
  channelValueList: LightingActionChannelValue[] = [];

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.universeName = data.universeName;

    if (data.channelValueList) {
      for (let channelValue of data.channelValueList) {
        this.channelValueList.push(
          new LightingActionChannelValue(channelValue)
        );
      }
    }
  }
}
