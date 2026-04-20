package com.ascargon.rocketshow.update;

import org.springframework.stereotype.Service;

@Service
public interface VersionService {

    VersionInfo getCurrentVersionInfo() throws Exception;

    VersionInfo getRemoteVersionInfo(boolean testBranch) throws Exception;

}
