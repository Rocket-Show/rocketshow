package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DefaultCompositionFileService implements CompositionFileService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultCompositionFileService.class);

    private final SettingsService settingsService;

    public DefaultCompositionFileService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public List<CompositionFile> getAllFiles() {
        List<CompositionFile> returnCompositionFileList = new ArrayList<>();
        File folder;
        File[] fileList;

        // Audio files
        folder = new File(
                settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getAudioPath());
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
        folder = new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getMidiPath());
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
                settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getVideoPath());
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

    @Override
    public void deleteFile(String name, String type) {
        String path = settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator;

        // Audio files
        if ("MIDI".equals(type)) {
            path += settingsService.getSettings().getMidiPath();
        } else if ("AUDIO".equals(type)) {
            path += settingsService.getSettings().getAudioPath();
        } else if ("VIDEO".equals(type)) {
            path += settingsService.getSettings().getVideoPath();
        }

        path += File.separator + name;

        logger.info("Delete file '" + path + "'");

        File systemFile = new File(path);

        if (!systemFile.exists()) {
            return;
        }

        boolean result = systemFile.delete();

        if (!result) {
            logger.error("Could not delete file '" + name + "'");
        }
    }

    private void createDirectoryIfNotExists(String directory) throws IOException {
        Path path = Paths.get(directory);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }


    @Override
    public CompositionFile saveFile(InputStream uploadedInputStream, String fileName) {
        String[] midiFormats = {"midi", "mid"};
        String[] audioFormats = {"wav", "wave", "mp3", "aac", "ogg", "oga", "mogg", "wma"};
        String[] videoFormats = {"avi", "mpg", "mpeg", "mkv", "mp4", "mov", "m4a"};

        CompositionFile compositionFile = null;

        // Compute the path according to the file extension
        String extension = FilenameUtils.getExtension(fileName).toLowerCase().trim();
        String path = settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator;

        if (Arrays.asList(midiFormats).contains(extension)) {
            path += settingsService.getSettings().getMidiPath();
            compositionFile = new MidiCompositionFile();
        } else if (Arrays.asList(audioFormats).contains(extension)) {
            path += settingsService.getSettings().getAudioPath();
            compositionFile = new AudioCompositionFile();
        } else if (Arrays.asList(videoFormats).contains(extension)) {
            path += settingsService.getSettings().getVideoPath();
            compositionFile = new VideoCompositionFile();
        }

        path += File.separator;

        if (compositionFile == null) {
            // We could not determine the file type -> don't store the file
            return null;
        }

        try {
            createDirectoryIfNotExists(path);
        } catch (IOException e) {
            logger.error("Could not create directory to save the file", e);
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
