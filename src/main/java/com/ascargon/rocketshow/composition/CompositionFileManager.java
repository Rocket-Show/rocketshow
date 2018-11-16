package com.ascargon.rocketshow.composition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

public class CompositionFileManager {

    private final static Logger logger = Logger.getLogger(CompositionFileManager.class);

    public List<CompositionFile> getAllFiles() {
        List<CompositionFile> returnCompositionFileList = new ArrayList<>();
        File folder;
        File[] fileList;

        // Audio files
        folder = new File(
                Manager.BASE_PATH + CompositionFile.MEDIA_PATH + AudioCompositionFile.AUDIO_PATH);
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    AudioCompositionFile audioFile = new AudioCompositionFile();
                    audioFile.setName(file.getName());
                    returnCompositionFileList.add(audioFile);
                }
            }
        }

        // MIDI files
        folder = new File(Manager.BASE_PATH + CompositionFile.MEDIA_PATH + MidiCompositionFile.MIDI_PATH);
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    MidiCompositionFile midiFile = new MidiCompositionFile();
                    midiFile.setName(file.getName());
                    returnCompositionFileList.add(midiFile);
                }
            }
        }

        // Video files
        folder = new File(
                Manager.BASE_PATH + CompositionFile.MEDIA_PATH + VideoCompositionFile.VIDEO_PATH);
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    VideoCompositionFile videoFile = new VideoCompositionFile();
                    videoFile.setName(file.getName());
                    returnCompositionFileList.add(videoFile);
                }
            }
        }

        return returnCompositionFileList;
    }

    public void deleteFile(String name, String type) {
        String path = Manager.BASE_PATH + CompositionFile.MEDIA_PATH;

        // Audio files
        if (type.equals(CompositionFile.FileType.MIDI.name())) {
            path += MidiCompositionFile.MIDI_PATH;
        } else if (type.equals(CompositionFile.FileType.AUDIO.name())) {
            path += AudioCompositionFile.AUDIO_PATH;
        } else if (type.equals(CompositionFile.FileType.VIDEO.name())) {
            path += VideoCompositionFile.VIDEO_PATH;
        }

        path += name;

        File systemFile = new File(path);

        if (!systemFile.exists()) {
            return;
        }

        boolean result = systemFile.delete();

        if (!result) {
            logger.error("Could not delete file '" + name + "'");
        }
    }

    public CompositionFile saveFile(InputStream uploadedInputStream, String fileName) {
        String[] midiFormats = {"midi", "mid"};
        String[] audioFormats = {"wav", "wave", "mp3", "aac", "ogg", "oga", "mogg", "wma"};
        String[] videoFormats = {"avi", "mpg", "mpeg", "mkv", "mp4", "mov", "m4a"};

        CompositionFile compositionFile = null;

        // Compute the path according to the file extension
        String extension = FilenameUtils.getExtension(fileName).toLowerCase().trim();
        String path = Manager.BASE_PATH + CompositionFile.MEDIA_PATH;

        if (Arrays.asList(midiFormats).contains(extension)) {
            path += MidiCompositionFile.MIDI_PATH;
            compositionFile = new MidiCompositionFile();
        } else if (Arrays.asList(audioFormats).contains(extension)) {
            path += AudioCompositionFile.AUDIO_PATH;
            compositionFile = new AudioCompositionFile();
        } else if (Arrays.asList(videoFormats).contains(extension)) {
            path += VideoCompositionFile.VIDEO_PATH;
            compositionFile = new VideoCompositionFile();
        }

        if (compositionFile == null) {
            // We could not determine the file type -> don't store the file
            return null;
        }

        compositionFile.setName(fileName);

        path += fileName;

        try {
            OutputStream out;
            int read;
            byte[] bytes = new byte[1024];

            out = new FileOutputStream(new File(path));

            while ((read = uploadedInputStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }

            out.flush();
            out.close();
        } catch (IOException e) {
            logger.error("Could not save file '" + fileName + "'", e);
        }

        return compositionFile;
    }

}
