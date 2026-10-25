package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.sun.net.httpserver.HttpServer;
import ola.OlaClient;
import ola.proto.Ola;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Patching the DMX universes of the settings in OLA at the start: OLA is mocked, its web interface simulated.
 */
class OlaSetupTest {

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final OlaClient olaClient = mock(OlaClient.class);

    // What the web interface of OLA got, and the output ports it offers
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private String livePorts = "[]";
    private HttpServer ola;

    private DefaultLightingService lightingService;

    @BeforeEach
    void setUp() throws Exception {
        when(settingsService.getSettings()).thenReturn(settings);

        ola = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ola.createContext("/", exchange -> {
            String body = URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            byte[] answer = new byte[0];
            if (exchange.getRequestURI().getPath().equals("/json/get_ports")) {
                answer = livePorts.getBytes(StandardCharsets.UTF_8);
            } else {
                requests.add(exchange.getRequestURI().getPath() + " " + body);
            }
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        ola.start();

        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        when(capabilitiesService.getCapabilities()).thenReturn(new Capabilities());
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(new OperatingSystemInformation());

        lightingService = new DefaultLightingService(capabilitiesService, mock(ActivityNotificationLightingService.class), settingsService, operatingSystemInformationService);
        lightingService.olaUrl = "http://127.0.0.1:" + ola.getAddress().getPort() + "/";
        lightingService.setOlaClient(olaClient);
    }

    @AfterEach
    void tearDown() {
        ola.stop(0);
    }

    private LightingUniverse universe(int olaUniverseId, String name, String portId, String device) {
        LightingUniverse lightingUniverse = new LightingUniverse();
        lightingUniverse.setOlaUniverseId(olaUniverseId);
        lightingUniverse.setName(name);
        lightingUniverse.setOlaOutputPortId(portId);
        lightingUniverse.setOlaOutputPortDevice(device);
        settings.getLightingUniverseList().add(lightingUniverse);
        return lightingUniverse;
    }

    // The universes OLA has
    private void olaUniverses(Ola.UniverseInfo... universes) {
        Ola.UniverseInfoReply.Builder reply = Ola.UniverseInfoReply.newBuilder();
        for (Ola.UniverseInfo universe : universes) {
            reply.addUniverse(universe);
        }
        when(olaClient.getUniverseList()).thenReturn(reply.buildPartial());
        when(olaClient.getUniverseInfo(anyInt())).thenReturn(reply.buildPartial());
    }

    private static Ola.UniverseInfo olaUniverse(int id, String name) {
        return Ola.UniverseInfo.newBuilder().setUniverse(id).setName(name).buildPartial();
    }

    // A DMX interface of OLA with one output port, patched to the passed universe (or none)
    private void olaDevice(int alias, Integer patchedUniverse) {
        Ola.PortInfo.Builder port = Ola.PortInfo.newBuilder().setPortId(0);
        if (patchedUniverse != null) {
            port.setUniverse(patchedUniverse);
        }
        Ola.DeviceInfo device = Ola.DeviceInfo.newBuilder().setDeviceAlias(alias).addOutputPort(port.buildPartial()).buildPartial();
        when(olaClient.getDeviceInfo()).thenReturn(Ola.DeviceInfoReply.newBuilder().addDevice(device).buildPartial());
    }

    @Test
    void keepsAPatchWhichMatches() throws Exception {
        universe(1, "Stage", "3-O-0", "Enttec DMX USB Pro");
        olaUniverses(olaUniverse(1, "Stage"));
        olaDevice(3, 1);

        lightingService.setUpOlaUniverses();

        assertEquals(List.of(), requests);
        assertTrue(lightingService.isOlaReady());
        verify(settingsService, never()).save();
    }

    @Test
    void followsAnInterfaceWhichGotAnotherPort() throws Exception {
        // E.g. the USB interfaces were plugged in in another order
        LightingUniverse stage = universe(1, "Stage", "2-O-0", "Enttec DMX USB Pro");
        olaUniverses(olaUniverse(1, "Stage"));
        olaDevice(3, null);
        livePorts = "[{\"id\": \"3-O-0\", \"device\": \"Enttec DMX USB Pro\", \"is_output\": true}]";

        lightingService.setUpOlaUniverses();

        assertEquals("3-O-0", stage.getOlaOutputPortId());
        verify(settingsService).save();
        assertTrue(requests.contains("/modify_universe id=1&name=Stage&add_ports=3-O-0"), requests.toString());
        assertTrue(lightingService.isOlaReady());
    }

    @Test
    void createsAUniverseOlaDoesNotHaveYet() throws Exception {
        LightingUniverse back = universe(2, "Back", "1-O-0", null);
        olaUniverses();
        olaDevice(1, null);
        livePorts = "[{\"id\": \"1-O-0\", \"device\": \"DMX HAT\", \"is_output\": true}]";

        lightingService.setUpOlaUniverses();

        // The device of the port is stored, to follow it later
        assertEquals("DMX HAT", back.getOlaOutputPortDevice());
        assertTrue(requests.contains("/new_universe id=2&name=Back&add_ports=1-O-0"), requests.toString());
        verify(olaClient).setUniverseName(2, "Back");
        assertTrue(lightingService.isOlaReady());
    }

    @Test
    void sendsTheDmxValuesOncePatched() throws Exception {
        universe(1, "Stage", "3-O-0", "Enttec DMX USB Pro");
        olaUniverses(olaUniverse(1, "Stage"));
        olaDevice(3, 1);
        lightingService.setUpOlaUniverses();

        LightingUniverseState composition = new LightingUniverseState();
        composition.getUniverse().put(0, 255);
        lightingService.addLightingUniverse(composition);
        lightingService.setExternalSync(true);
        lightingService.sendExternalSync(false);

        short[] expected = new short[512];
        expected[0] = 255;
        verify(olaClient).sendDmx(1, expected);
    }

}
