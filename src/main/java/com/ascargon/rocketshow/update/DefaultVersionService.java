package com.ascargon.rocketshow.update;

import com.ascargon.rocketshow.RocketShowApplication;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

@Service
public class DefaultVersionService implements VersionService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultVersionService.class);

    private final static String UPDATE_URL = "https://www.rocketshow.net/update/";
    private final static String UPDATE_URL_TEST_SUFFIX = "test/";

    private final static String VERSION_FILE = "version.xml";

    @Override
    public VersionInfo getCurrentVersionInfo() throws Exception {
        File file = new File(new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + VERSION_FILE);

        if (!file.exists()) {
            logger.warn("No version file available at " + file);
            return new VersionInfo(); // return empty object if file does not exist
        }

        JAXBContext jaxbContext = JAXBContext.newInstance(VersionInfo.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

        return (VersionInfo) jaxbUnmarshaller.unmarshal(file);
    }

    private String getRemoteBaseUrl(boolean testBranch) {
        String url = UPDATE_URL;
        if (testBranch) {
            url += UPDATE_URL_TEST_SUFFIX;
        }
        return url;
    }

    @Override
    public VersionInfo getRemoteVersionInfo(boolean testBranch) throws Exception {
        URL url = new URL(getRemoteBaseUrl(testBranch) + VERSION_FILE);
        InputStream inputStream = url.openStream();

        JAXBContext jaxbContext = JAXBContext.newInstance(VersionInfo.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        return (VersionInfo) jaxbUnmarshaller.unmarshal(inputStream);
    }

}
