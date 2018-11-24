package com.ascargon.rocketshow.util;

public interface UpdateService {

    enum UpdateState {
        DOWNLOADING, INSTALLING, REBOOTING
    }

    VersionInfo getCurrentVersionInfo() throws Exception;

    VersionInfo getRemoteVersionInfo() throws Exception;

    void update() throws Exception;

}
