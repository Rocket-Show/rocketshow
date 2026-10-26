package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.util.BackupService;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * The download of a backup, which is written while it's downloaded, in a real web server: a backup failing after the
 * download started must not end like a complete backup.
 */
class BackupDownloadTest {

    private final static BackupService backupService = mock(BackupService.class);

    @TempDir
    Path directory;

    private Tomcat tomcat;

    @Configuration
    @EnableWebMvc
    @Import(BackupController.class)
    static class WebConfig {

        @Bean
        BackupService backupService() {
            return backupService;
        }

    }

    @BeforeEach
    void startServer() throws Exception {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(WebConfig.class);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("spring.data.rest.base-path", "/api")));

        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context servletContext = tomcat.addContext("", directory.toString());
        Tomcat.addServlet(servletContext, "dispatcher", new DispatcherServlet(context)).setLoadOnStartup(1);
        servletContext.addServletMappingDecoded("/", "dispatcher");
        tomcat.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    private HttpResponse<byte[]> download() throws Exception {
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            URI uri = URI.create("http://localhost:" + tomcat.getConnector().getLocalPort() + "/api/system/create-backup");
            return httpClient.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    @Test
    void downloadsTheBackup() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(0, OutputStream.class).write("backup".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(backupService).create(any());

        HttpResponse<byte[]> response = download();

        assertEquals(200, response.statusCode());
        assertEquals("backup", new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("attachment; filename=\"backup.tar.gz\"", response.headers().firstValue("Content-Disposition").orElseThrow());
    }

    @Test
    void breaksOffTheDownloadWhenTheBackupFails() throws Exception {
        // E.g. a file which can't be read, after a part of the backup was sent
        doAnswer(invocation -> {
            invocation.getArgument(0, OutputStream.class).write(new byte[1024 * 1024]);
            throw new Exception("Could not create the backup: tar: media/audio/song.wav: Cannot open: Permission denied");
        }).when(backupService).create(any());

        assertThrows(IOException.class, this::download);
    }

}
