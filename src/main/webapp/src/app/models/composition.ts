import { LeadSheet } from './lead-sheet';
import { CompositionVideoFile } from './composition-video-file';
import { CompositionMidiFile } from "./composition-midi-file";
import { CompositionAudioFile } from "./composition-audio-file";
import { CompositionFile } from "./composition-file";

export class Composition {
    name: string;
    durationMillis: number;
    fileList: CompositionFile[] = [];
    notes: string;
    autoStartNextComposition: boolean = false;
    leadSheetList: LeadSheet[] = [];

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
    }

    private parseFileList(data: any): CompositionFile[] {
        let fileList: CompositionFile[] = [];

        if (data.fileList) {
            for (let file of data.fileList) {
                if (file.midiFile) {
                    let midiFile = new CompositionMidiFile(file.midiFile);
                    fileList.push(midiFile);
                } else if (file.audioFile) {
                    let audioFile = new CompositionAudioFile(file.audioFile);
                    fileList.push(audioFile);
                } else if (file.videoFile) {
                    let videoFile = new CompositionVideoFile(file.videoFile);
                    fileList.push(videoFile);
                }
            }
        }

        return fileList;
    }

    // Return a file object based on its type
    public static getFileObjectByType(data: any) {
        if (data.type == 'MIDI') {
            let midiFile = new CompositionMidiFile(data);
            return midiFile;
        } else if (data.type == 'AUDIO') {
            let audioFile = new CompositionAudioFile(data);
            return audioFile;
        } else if (data.type == 'VIDEO') {
            let videoFile = new CompositionVideoFile(data);
            return videoFile;
        }
    }

    // Stringify the composition and it's files correct (JSON would ignore the extended file classes by default)
    stringify(): string {
        let string = JSON.stringify(this);
        let object = JSON.parse(string);

        object.fileList = [];

        // These properties belong to the set compositions -> remove them
        object.autoStartNextComposition = undefined;

        for (let file of this.fileList) {
            if (file instanceof CompositionMidiFile) {
                let fileObj: any = {};
                fileObj.midiFile = file;
                object.fileList.push(fileObj);
            } else if (file instanceof CompositionAudioFile) {
                let fileObj: any = {};
                fileObj.audioFile = file;
                object.fileList.push(fileObj);
            } else if (file instanceof CompositionVideoFile) {
                let fileObj: any = {};
                fileObj.videoFile = file;
                object.fileList.push(fileObj);
            }
        }

        return JSON.stringify(object);
    }
}
