package com.ascargon.rocketshow.update;

import org.springframework.stereotype.Service;

@Service
public interface VersionService {

    VersionInfo getCurrentVersionInfo() throws Exception;

    String getRemoteBaseUrl(boolean testBranch);

    VersionInfo getRemoteVersionInfo(boolean testBranch) throws Exception;

}
