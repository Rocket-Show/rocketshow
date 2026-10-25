package com.ascargon.rocketshow.session;

import com.ascargon.rocketshow.composition.DefaultSetService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a unit remembers over a restart, e.g. the set it played.
 */
class DefaultSessionServiceTest {

    @TempDir
    Path directory;

    @Test
    void remembersTheSetAndSettingsOverARestart() {
        SettingsService settingsService = TestSettings.inDirectory(directory);
        DefaultSetService setService = new DefaultSetService();
        Set set = new Set();
        set.setName("Show");
        setService.setCurrentSet(set);

        DefaultSessionService sessionService = new DefaultSessionService(settingsService, setService);
        sessionService.getSession().setAutoSelectNextComposition(true);
        sessionService.save();

        Session session = new DefaultSessionService(settingsService, new DefaultSetService()).getSession();
        assertEquals("Show", session.getCurrentSetName());
        assertTrue(session.isAutoSelectNextComposition());
    }

    @Test
    void startsANewSessionWhenTheOldOneCannotBeRead() throws Exception {
        SettingsService settingsService = TestSettings.inDirectory(directory);
        Files.writeString(directory.resolve("session.xml"), "<session><currentSetName>Sh");

        // The player needs a session, e.g. at the end of every composition
        Session session = new DefaultSessionService(settingsService, new DefaultSetService()).getSession();

        assertNotNull(session);
        assertFalse(session.isAutoSelectNextComposition());
    }

}
