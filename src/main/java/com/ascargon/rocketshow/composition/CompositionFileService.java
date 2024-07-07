package com.ascargon.rocketshow.composition;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.util.List;

@Service
public interface CompositionFileService {

    List<CompositionFile> getAllFiles();

    void deleteFile(String name, String type);

    void saveFileInit(String fileName) throws Exception;

    void saveFileAddChunk(InputStream inputStream, String fileName) throws Exception;

    CompositionFile saveFileFinish(String fileName) throws Exception;

    File getFile(String name, String type) throws Exception;

}
