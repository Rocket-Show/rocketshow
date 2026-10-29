import { Composition } from "./composition";

export class Set {
  currentCompositionIndex: number;
  compositionList: Composition[] = [];
  name: string;
  notes: string;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.currentCompositionIndex = data.currentCompositionIndex;
    this.notes = data.notes;
    this.name = data.name;

    this.compositionList = [];

    if (data.compositionList) {
      for (let composition of data.compositionList) {
        this.compositionList.push(new Composition(composition));
      }
    }
  }

  toJSON() {
    return {
      ...this,
      compositionList:
        this.compositionList?.map(
          ({
            name,
            durationMillis,
            autoStartNextComposition,
            timecodeStartMillis,
            midiNumber,
            showControlCue,
          }) => ({
            name,
            durationMillis,
            autoStartNextComposition,
            timecodeStartMillis: timecodeStartMillis || 0,
            midiNumber,
            showControlCue,
          })
        ) || [],

      // Exclude some properties
      currentCompositionIndex: undefined,
    };
  }
}
