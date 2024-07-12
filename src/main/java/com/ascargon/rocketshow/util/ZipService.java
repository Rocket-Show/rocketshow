package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipOutputStream;

@Service
public interface ZipService {

    void zipFile(File directory, String fileName, ZipOutputStream zipOut, List<String> ignoreFileNameList) throws IOException;

    void unzipFile(String zipFile, File destDir) throws IOException;

}
