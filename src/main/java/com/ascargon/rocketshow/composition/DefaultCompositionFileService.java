package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.util.ChunkedFileUploadService;
import com.ascargon.rocketshow.util.FileFilterService;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final FileFilterService fileFilterService;
    private final ChunkedFileUploadService chunkedFileUploadService;

    public DefaultCompositionFileService(
            SettingsService settingsService,
            FileFilterService fileFilterService,
            ChunkedFileUploadService chunkedFileUploadService
    ) {
        this.settingsService = settingsService;
        this.fileFilterService = fileFilterService;
        this.chunkedFileUploadService = chunkedFileUploadService;
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
                if (file.isFile() && !fileFilterService.filterFile(file.getName())) {
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
                if (file.isFile() && !fileFilterService.filterFile(file.getName())) {
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
                if (file.isFile() && !fileFilterService.filterFile(file.getName())) {
                    VideoCompositionFile videoFile = new VideoCompositionFile();
                    videoFile.setName(file.getName());
                    returnCompositionFileList.add(videoFile);
                }
            }
        }

        return returnCompositionFileList;
    }

    private String getPath(String name, String type) {
        String path = settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator;

        if ("MIDI".equals(type)) {
            path += settingsService.getSettings().getMidiPath();
        } else if ("AUDIO".equals(type)) {
            path += settingsService.getSettings().getAudioPath();
        } else if ("VIDEO".equals(type)) {
            path += settingsService.getSettings().getVideoPath();
        }

        path += File.separator + name;

        return path;
    }

    @Override
    public void deleteFile(String name, String type) {
        String path = getPath(name, type);

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

    private CompositionFile.CompositionFileType saveFileGetFileType(String fileName) throws Exception {
        String[] midiFormats = {"midi", "mid"};
        String[] audioFormats = {"wav", "wave", "mp3", "aac", "ogg", "oga", "mogg", "wma"};
        String[] videoFormats = {"avi", "mpg", "mpeg", "mkv", "mp4", "mov", "m4a", "m4v"};

        String extension = FilenameUtils.getExtension(fileName).toLowerCase().trim();

        if (Arrays.asList(midiFormats).contains(extension)) {
            return CompositionFile.CompositionFileType.MIDI;
        } else if (Arrays.asList(audioFormats).contains(extension)) {
            return CompositionFile.CompositionFileType.AUDIO;
        } else if (Arrays.asList(videoFormats).contains(extension)) {
            return CompositionFile.CompositionFileType.VIDEO;
        } else {
            throw new Exception("No valid file format");
        }
    }

    private File saveFileGetFile(String fileName) throws Exception {
        CompositionFile.CompositionFileType fileType = saveFileGetFileType(fileName);

        String path = settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator;

        switch (fileType) {
            case MIDI -> {
                path += settingsService.getSettings().getMidiPath();
            }
            case AUDIO -> {
                path += settingsService.getSettings().getAudioPath();
            }
            case VIDEO -> {
                path += settingsService.getSettings().getVideoPath();
            }
        }

        path += File.separator;

        try {
            createDirectoryIfNotExists(path);
        } catch (IOException e) {
            logger.error("Could not create directory to save the file", e);
        }

        path += fileName;

        return new File(path);
    }

    @Override
    public void saveFileInit(String fileName) throws Exception {
        File file = saveFileGetFile(fileName);
        if (file.exists()) {
            boolean result = file.delete();
            if (!result) {
                throw new Exception("Could not delete composition file '" + file.getPath() + "'");
            }
        }
    }

    @Override
    public void saveFileAddChunk(InputStream inputStream, String fileName) throws Exception {
        chunkedFileUploadService.handleChunk(inputStream, saveFileGetFile(fileName));
    }

    @Override
    public CompositionFile saveFileFinish(String fileName) throws Exception {
        CompositionFile compositionFile = null;
        CompositionFile.CompositionFileType fileType = saveFileGetFileType(fileName);

        switch (fileType) {
            case MIDI -> {
                compositionFile = new MidiCompositionFile();
            }
            case AUDIO -> {
                compositionFile = new AudioCompositionFile();
            }
            case VIDEO -> {
                compositionFile = new VideoCompositionFile();
            }
        }

        compositionFile.setName(fileName);

        return compositionFile;
    }

    @Override
    public File getFile(String name, String type) throws Exception {
        String path = getPath(name, type);
        return new File(path);
    }

}
