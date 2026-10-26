package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.settings.ApiKey;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.SettingsUpdateSystemService;
import com.ascargon.rocketshow.update.UpdateService;
import com.ascargon.rocketshow.util.FactoryResetService;
import com.ascargon.rocketshow.util.RebootService;
import com.ascargon.rocketshow.util.ShutdownService;
import com.ascargon.rocketshow.util.SshService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calls the API of the whole app through its security filter chain, like the web app and other units do.
 */
@SpringBootTest
abstract class ApiTest {

    final static String ADMIN_PASSWORD = "admin-password";
    final static String API_KEY = "api-key-of-another-unit";

    // Saving the settings writes system files (e.g. ~/.asoundrc) and runs system commands
    @MockitoBean
    SettingsUpdateSystemService settingsUpdateSystemService;

    // Never really run these, not even when a test wrongly gets access
    @MockitoBean
    RebootService rebootService;

    @MockitoBean
    ShutdownService shutdownService;

    @MockitoBean
    FactoryResetService factoryResetService;

    @MockitoBean
    SshService sshService;

    @MockitoBean
    UpdateService updateService;

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    Filter springSecurityFilterChain;

    @Autowired
    SettingsService settingsService;

    @Autowired
    LoginThrottleService loginThrottleService;

    MockMvc mockMvc;

    // The settings the tests change, to restore them afterwards
    private String language;
    private String deviceName;
    private Boolean wlanApEnable;
    private String wlanApPassphrase;
    private Boolean tlsEnable;

    @BeforeEach
    void setUpApi() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(springSecurityFilterChain).build();

        Settings settings = settingsService.getSettings();
        language = settings.getLanguage();
        deviceName = settings.getDeviceName();
        wlanApEnable = settings.getWlanApEnable();
        wlanApPassphrase = settings.getWlanApPassphrase();
        tlsEnable = settings.getTlsEnable();

        resetAccess();
    }

    @AfterEach
    void restoreSettings() throws Exception {
        Settings settings = settingsService.getSettings();
        settings.setLanguage(language);
        settings.setDeviceName(deviceName);
        settings.setWlanApEnable(wlanApEnable);
        settings.setWlanApPassphrase(wlanApPassphrase);
        settings.setTlsEnable(tlsEnable);

        resetAccess();
        settingsService.save();
    }

    // Like a new unit: no admin password, no API keys, no failed logins
    private void resetAccess() {
        settingsService.getSettings().setAdminPasswordHash(null);
        settingsService.getSettings().getApiKeyList().clear();
        loginThrottleService.onSuccess();
    }

    // Finish the initial setup by setting the admin password
    void setAdminPassword() {
        settingsService.getSettings().setAdminPasswordHash(new BCryptPasswordEncoder().encode(ADMIN_PASSWORD));
    }

    MockHttpSession loginAsAdmin() throws Exception {
        setAdminPassword();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    ApiKey addApiKey() {
        ApiKey apiKey = new ApiKey();
        apiKey.setUuid("another-unit");
        apiKey.setKeyHash(new BCryptPasswordEncoder().encode(API_KEY));
        settingsService.getSettings().getApiKeyList().add(apiKey);
        return apiKey;
    }

    int getStatus(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    void assertAllowed(MockHttpServletRequestBuilder request) throws Exception {
        int status = getStatus(request);
        assertTrue(status != 401 && status != 403, "Expected access, got " + status);
    }

    void assertDenied(MockHttpServletRequestBuilder request) throws Exception {
        int status = getStatus(request);
        assertTrue(status == 401 || status == 403, "Expected no access, got " + status);
    }

}
