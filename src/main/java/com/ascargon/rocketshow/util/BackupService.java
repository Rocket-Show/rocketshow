package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.File;

@Service
public interface BackupService {

    File createBackup() throws Exception;

}
