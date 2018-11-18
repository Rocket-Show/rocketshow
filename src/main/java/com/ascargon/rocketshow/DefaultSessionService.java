package com.ascargon.rocketshow;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.List;

@Service
public class DefaultSessionService implements SessionService {

    private final static Logger logger = Logger.getLogger(DefaultSessionService.class);

    private SettingsService settingsService;
    private SetService setService;
    private CompositionService compositionService;
    private PlayerService playerService;
    private NotificationService notificationService;

    private Session session;

    public DefaultSessionService(SettingsService settingsService, SetService setService, CompositionService compositionService, PlayerService playerService, NotificationService notificationService) {
        this.settingsService = settingsService;
        this.setService = setService;
        this.compositionService = compositionService;
        this.playerService = playerService;
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

        // Load the last set/composition
        if (session != null && session.getCurrentSetName() != null && session.getCurrentSetName().length() > 0) {
            // Load the last set
            loadSetAndComposition(session.getCurrentSetName());
        } else {
            // Load the default set
            loadSetAndComposition("");
        }
    }

    private void loadSetAndComposition(String setName) throws Exception {
        if (setName.length() > 0) {
            setService.setCurrentSet(compositionService.getSet(setName));
        }

        // Read the current composition file
        if (setService.getCurrentSet() == null) {
            // We have no set. Simply read the first composition, if available
            logger.debug("Try setting an initial composition...");

            List<Composition> compositions = compositionService.getAllCompositions();

            if (compositions.size() > 0) {
                logger.debug("Set initial composition '" + compositions.get(0).getName() + "'...");

                playerService.setComposition(compositions.get(0));
            }
        } else {
            // We got a set loaded
            try {
                setService.readCurrentComposition();
            } catch (Exception e) {
                logger.error("Could not read current composition", e);
            }
        }

        notificationService.notifyClients();

        save();
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
