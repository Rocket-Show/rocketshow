package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.util.BackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads a backup, written while it's downloaded. Without an exception handler on purpose: a backup failing after
 * the download started must break off the download, instead of ending it like a complete backup.
 */
@RestController
@RequestMapping("${spring.data.rest.base-path}/system")
@CrossOrigin
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("create-backup")
    public void createBackup(HttpServletResponse response) throws Exception {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=\"backup.tar.gz\"");
        backupService.create(response.getOutputStream());
    }

}
