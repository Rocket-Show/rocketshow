package com.ascargon.rocketshow;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;

@Service
public class DefaultSessionService implements SessionService {

    private final static Logger logger = LogManager.getLogger(DefaultSessionService.class);

    private SettingsService settingsService;
    private SetService setService;
    private CompositionService compositionService;
    private NotificationService notificationService;

    private Session session;

    public DefaultSessionService(SettingsService settingsService, SetService setService, CompositionService compositionService, NotificationService notificationService) {
        this.settingsService = settingsService;
        this.setService = setService;
        this.compositionService = compositionService;
        this.notificationService = notificationService;

        try {
            loadSession();
        } catch (Exception e) {
            logger.error("Could not restore session", e);
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
            File file = new File(settingsService.getSettings().getBasePath() + "/" + "session");
            JAXBContext jaxbContext = JAXBContext.newInstance(Session.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

            // output pretty printed
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            jaxbMarshaller.marshal(session, file);

            logger.info("Session saved");
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }

    private void loadSession() throws Exception {
        File file = new File(settingsService.getSettings().getBasePath() + "/" + "session");

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
