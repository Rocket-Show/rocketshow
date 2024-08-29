package com.ascargon.rocketshow.util;

public interface UpdateService {

    enum UpdateState {
        DOWNLOADING, INSTALLING, REBOOTING
    }

    VersionInfo getCurrentVersionInfo() throws Exception;

    VersionInfo getRemoteVersionInfo(boolean testBranch) throws Exception;

    void update(boolean testBranch) throws Exception;

}
