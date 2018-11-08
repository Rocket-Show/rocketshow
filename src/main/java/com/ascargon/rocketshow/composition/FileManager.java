package com.ascargon.rocketshow.composition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.audio.AudioFile;
import com.ascargon.rocketshow.midi.MidiFile;
import com.ascargon.rocketshow.video.VideoFile;

public class FileManager {

    private final static Logger logger = Logger.getLogger(FileManager.class);

    public List<com.ascargon.rocketshow.composition.File> getAllFiles() {
        List<com.ascargon.rocketshow.composition.File> returnFileList = new ArrayList<>();
        File folder;
        File[] fileList;

        // Audio files
        folder = new File(
                Manager.BASE_PATH + com.ascargon.rocketshow.composition.File.MEDIA_PATH + AudioFile.AUDIO_PATH);
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    AudioFile audioFile = new AudioFile();
                    audioFile.setName(file.getName());
                    returnFileList.add(audioFile);
                }
            }
        }

        // MIDI files
        folder = new File(Manager.BASE_PATH + com.ascargon.rocketshow.composition.File.MEDIA_PATH + MidiFile.MIDI_PATH);
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    MidiFile midiFile = new MidiFile();
                    midiFile.setName(file.getName());
                    returnFileList.add(midiFile);
                }
            }
        }

        // Video files
        folder = new File(
                Manager.BASE_PATH + com.ascargon.rocketshow.composition.File.MEDIA_PATH + VideoFile.VIDEO_PATH);
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    VideoFile videoFile = new VideoFile();
                    videoFile.setName(file.getName());
                    returnFileList.add(videoFile);
                }
            }
        }

        return returnFileList;
    }

    public void deleteFile(String name, String type) {
        String path = Manager.BASE_PATH + com.ascargon.rocketshow.composition.File.MEDIA_PATH;

        // Audio files
        if (type.equals(com.ascargon.rocketshow.composition.File.FileType.MIDI.name())) {
            path += MidiFile.MIDI_PATH;
        } else if (type.equals(com.ascargon.rocketshow.composition.File.FileType.AUDIO.name())) {
            path += AudioFile.AUDIO_PATH;
        } else if (type.equals(com.ascargon.rocketshow.composition.File.FileType.VIDEO.name())) {
            path += VideoFile.VIDEO_PATH;
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

    public com.ascargon.rocketshow.composition.File saveFile(InputStream uploadedInputStream, String fileName) {
        String[] midiFormats = {"midi", "mid"};
        String[] audioFormats = {"wav", "wave", "mp3", "aac", "ogg", "oga", "mogg", "wma"};
        String[] videoFormats = {"avi", "mpg", "mpeg", "mkv", "mp4", "mov", "m4a"};

        com.ascargon.rocketshow.composition.File file = null;

        // Compute the path according to the file extension
        String extension = FilenameUtils.getExtension(fileName).toLowerCase().trim();
        String path = Manager.BASE_PATH + com.ascargon.rocketshow.composition.File.MEDIA_PATH;

        if (Arrays.asList(midiFormats).contains(extension)) {
            path += MidiFile.MIDI_PATH;
            file = new MidiFile();
        } else if (Arrays.asList(audioFormats).contains(extension)) {
            path += AudioFile.AUDIO_PATH;
            file = new AudioFile();
        } else if (Arrays.asList(videoFormats).contains(extension)) {
            path += VideoFile.VIDEO_PATH;
            file = new VideoFile();
        }

        if (file == null) {
            // We could not determine the file type -> don't store the file
            return null;
        }

        file.setName(fileName);

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

        return file;
    }

}
