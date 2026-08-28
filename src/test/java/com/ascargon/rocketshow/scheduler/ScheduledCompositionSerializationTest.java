package com.ascargon.rocketshow.scheduler;

import com.ascargon.rocketshow.settings.Settings;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Make sure the scheduled compositions survive the settings file and the communication with the webapp.
 */
class ScheduledCompositionSerializationTest {

    private Settings marshalAndUnmarshal(Settings settings) throws Exception {
        JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);

        Marshaller marshaller = jaxbContext.createMarshaller();
        StringWriter stringWriter = new StringWriter();
        marshaller.marshal(settings, stringWriter);

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return (Settings) unmarshaller.unmarshal(new StringReader(stringWriter.toString()));
    }

    @Test
    void keepsTheScheduledCompositionsInTheSettingsFile() throws Exception {
        Settings settings = new Settings();

        ScheduledComposition interval = new ScheduledComposition();
        interval.setUuid("uuid-1");
        interval.setCompositionName("Opening show");
        interval.setScheduleType(ScheduledComposition.ScheduleType.INTERVAL);
        interval.setIntervalValue(2);
        interval.setIntervalUnit(ScheduledComposition.IntervalUnit.WEEKS);
        settings.getScheduledCompositionList().add(interval);

        ScheduledComposition weekly = new ScheduledComposition();
        weekly.setUuid("uuid-2");
        weekly.setEnabled(false);
        weekly.setCompositionName("Weekend show");
        weekly.setScheduleType(ScheduledComposition.ScheduleType.WEEKLY);
        weekly.setWeekdayList(List.of(6, 7));
        weekly.setTimeOfDay("20:00");
        settings.getScheduledCompositionList().add(weekly);

        ScheduledComposition yearly = new ScheduledComposition();
        yearly.setUuid("uuid-3");
        yearly.setCompositionName("New year show");
        yearly.setScheduleType(ScheduledComposition.ScheduleType.YEARLY);
        yearly.setMonthOfYear(12);
        yearly.setDayOfMonth(31);
        yearly.setTimeOfDay("23:55");
        settings.getScheduledCompositionList().add(yearly);

        Settings loaded = marshalAndUnmarshal(settings);

        assertEquals(settings.getScheduledCompositionList(), loaded.getScheduledCompositionList());
    }

    @Test
    void keepsAnEmptyWeekdaySelectionEmpty() throws Exception {
        Settings settings = new Settings();

        ScheduledComposition weekly = new ScheduledComposition();
        weekly.setCompositionName("Weekend show");
        weekly.setScheduleType(ScheduledComposition.ScheduleType.WEEKLY);
        settings.getScheduledCompositionList().add(weekly);

        Settings loaded = marshalAndUnmarshal(settings);

        assertTrue(loaded.getScheduledCompositionList().getFirst().getWeekdayList().isEmpty());
    }

    @Test
    void readsTheScheduledCompositionsOfTheWebapp() {
        String json = """
                {"scheduledCompositionList":[
                  {"uuid":"uuid-1","enabled":true,"compositionName":"Weekend show","scheduleType":"WEEKLY","intervalValue":5,"intervalUnit":"MINUTES","timeOfDay":"20:00","weekdayList":[6,7],"dayOfMonth":1,"monthOfYear":1},
                  {"uuid":"uuid-2","enabled":false,"compositionName":"New year show","scheduleType":"YEARLY","intervalValue":2,"intervalUnit":"DAYS","timeOfDay":"23:55","weekdayList":[],"dayOfMonth":31,"monthOfYear":12}
                ]}""";

        ObjectMapper objectMapper = new ObjectMapper();
        Settings settings = objectMapper.readValue(json, Settings.class);

        assertEquals(2, settings.getScheduledCompositionList().size());

        ScheduledComposition weekly = settings.getScheduledCompositionList().getFirst();
        assertEquals("uuid-1", weekly.getUuid());
        assertTrue(weekly.isEnabled());
        assertEquals("Weekend show", weekly.getCompositionName());
        assertEquals(ScheduledComposition.ScheduleType.WEEKLY, weekly.getScheduleType());
        assertEquals(List.of(6, 7), weekly.getWeekdayList());
        assertEquals("20:00", weekly.getTimeOfDay());

        ScheduledComposition yearly = settings.getScheduledCompositionList().get(1);
        assertFalse(yearly.isEnabled());
        assertEquals(ScheduledComposition.ScheduleType.YEARLY, yearly.getScheduleType());
        assertEquals(ScheduledComposition.IntervalUnit.DAYS, yearly.getIntervalUnit());
        assertTrue(yearly.getWeekdayList().isEmpty());
        assertEquals(12, yearly.getMonthOfYear());
        assertEquals(31, yearly.getDayOfMonth());

        // ... and sends them back to the webapp
        assertTrue(objectMapper.writeValueAsString(settings).contains("\"weekdayList\":[6,7]"));
    }

}
