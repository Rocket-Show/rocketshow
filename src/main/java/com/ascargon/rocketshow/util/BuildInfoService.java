package com.ascargon.rocketshow.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class BuildInfoService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @Autowired(required = false)
    private BuildProperties buildProperties;

    public BuildInfo getBuildInfo() {
        BuildInfo info = new BuildInfo();

        if (buildProperties == null) {
            return info;
        }

        if (buildProperties.getTime() != null) {
            info.setBuildDate(DATE_FORMATTER.format(buildProperties.getTime()));
            info.setBuildTimestamp(TIMESTAMP_FORMATTER.format(buildProperties.getTime()));
        }

        info.setBuildNumber(buildProperties.get("buildNumber"));

        return info;
    }
}
