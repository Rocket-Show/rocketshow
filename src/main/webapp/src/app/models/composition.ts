import { LeadSheet } from "./lead-sheet";
import { CompositionVideoFile } from "./composition-video-file";
import { CompositionMidiFile } from "./composition-midi-file";
import { CompositionAudioFile } from "./composition-audio-file";
import { CompositionFile } from "./composition-file";
import { ActionTriggerComposition } from "./action-trigger-composition";

export class Composition {
  name: string;
  durationMillis: number;
  fileList: CompositionFile[] = [];
  notes: string;
  autoStartNextComposition: boolean = false;
  leadSheetList: LeadSheet[] = [];
  loop: boolean;
  audioVolume: number = 1;
  actionTriggerList: ActionTriggerComposition[] = [];

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.name = data.name;
    this.durationMillis = data.durationMillis;
    this.notes = data.notes;
    this.autoStartNextComposition = data.autoStartNextComposition;

    this.fileList = this.parseFileList(data);

    if (data.leadSheetList) {
      for (let leadSheet of data.leadSheetList) {
        this.leadSheetList.push(new LeadSheet(leadSheet));
      }
    }

    this.loop = data.loop;
    this.audioVolume = data.audioVolume;

    if (data.actionTriggerList) {
      this.actionTriggerList = [];

      for (let actionTrigger of data.actionTriggerList) {
        this.actionTriggerList.push(
          new ActionTriggerComposition(actionTrigger)
        );
      }
    }
  }

  public static getFileObjectByType(data: any) {
    if (data.midiFile) {
      let midiFile = new CompositionMidiFile(data.midiFile);
      return midiFile;
    } else if (data.audioFile) {
      let audioFile = new CompositionAudioFile(data.audioFile);
      return audioFile;
    } else if (data.videoFile) {
      let videoFile = new CompositionVideoFile(data.videoFile);
      return videoFile;
    }
  }

  private parseFileList(data: any): CompositionFile[] {
    let fileList: CompositionFile[] = [];

    if (data.fileList) {
      for (let file of data.fileList) {
        fileList.push(Composition.getFileObjectByType(file));
      }
    }

    return fileList;
  }

  toJSON() {
    return {
      ...this,
      // Exclude unwanted properties
      autoStartNextComposition: undefined,
    };
  }
}
