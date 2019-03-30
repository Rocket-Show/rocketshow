package com.ascargon.rocketshow.composition;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public interface CompositionFileService {

    List<CompositionFile> getAllFiles();

    void deleteFile(String name, String type);

    CompositionFile saveFile(InputStream uploadedInputStream, String fileName) throws Exception;

}
