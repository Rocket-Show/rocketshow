package com.ascargon.rocketshow;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.SetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DefaultSessionService implements SessionService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultSessionService.class);

    private final String FILE_NAME = "session";

    private final SettingsService settingsService;
    private final SetService setService;
    private final NotificationService notificationService;

    private Session session;

    public DefaultSessionService(SettingsService settingsService, SetService setService, NotificationService notificationService) {
        this.settingsService = settingsService;
        this.setService = setService;
        this.notificationService = notificationService;

        try {
            loadSession();
        } catch (Exception e) {
            logger.error("Could not restore session", e);
        }
    }

    private void createDirectoryIfNotExists(String directory) throws IOException {
        Path path = Paths.get(directory);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }

    @Override
    public void save() {
        if (setService.getCurrentSet() == null) {
            session.setCurrentSetName("");
        } else {
            session.setCurrentSetName(setService.getCurrentSet().getName());
        }

        try {
            String directory = settingsService.getSettings().getBasePath();
            createDirectoryIfNotExists(directory);

            File file = new File(directory + "/" + FILE_NAME + ".xml");
            JAXBContext jaxbContext = JAXBContext.newInstance(Session.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

            // output pretty printed
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            jaxbMarshaller.marshal(session, file);

            logger.info("Session saved");
        } catch (JAXBException | IOException e) {
            e.printStackTrace();
        }
    }

    private void loadSession() throws Exception {
        File file = new File(settingsService.getSettings().getBasePath() + "/" + FILE_NAME + ".xml");

        if (file.exists()) {
            // We already have a session -> restore it from the file
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Session.class);

                Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
                session = (Session) jaxbUnmarshaller.unmarshal(file);

                logger.info("Session restored");
            } catch (JAXBException e) {
                e.printStackTrace();
            }
        } else {
            // There is no session existant -> create a default session
            session = new Session();
            save();
        }

        notificationService.notifyClients(session.isUpdateFinished());
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

}
