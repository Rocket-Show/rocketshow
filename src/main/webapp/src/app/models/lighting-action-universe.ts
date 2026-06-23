import { LightingActionChannelValue } from "./lighting-action-channel-value";

export class LightingActionUniverse {
  universeUuid: string;
  channelValueList: LightingActionChannelValue[] = [];

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.universeUuid = data.universeUuid;

    if (data.channelValueList) {
      for (let channelValue of data.channelValueList) {
        this.channelValueList.push(
          new LightingActionChannelValue(channelValue)
        );
      }
    }
  }
}
