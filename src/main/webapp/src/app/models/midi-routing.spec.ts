import { MidiRouting } from "./midi-routing";

/**
 * A MIDI routing to other units, named like in the API of the unit, which ignores fields it doesn't know.
 */
describe("MIDI routing", () => {
  it("loads the other units of a routing as the unit sends them", () => {
    const midiRouting = new MidiRouting({
      midiDestination: "REMOTE",
      midiMapping: {},
      remoteDeviceNameList: ["Unit B"],
      remoteDeviceIdList: ["Unit B"],
    });

    expect(midiRouting.remoteDeviceNameList).toEqual(["Unit B"]);
  });

  it("sends the other units of a routing under the name the unit reads", () => {
    const midiRouting = new MidiRouting();
    midiRouting.midiDestination = "REMOTE";
    midiRouting.remoteDeviceNameList.push("Unit B");

    const saved = JSON.parse(JSON.stringify(midiRouting));

    expect(saved.remoteDeviceNameList).toEqual(["Unit B"]);
    expect(saved.remoteDeviceList).toBeUndefined();
  });
});
