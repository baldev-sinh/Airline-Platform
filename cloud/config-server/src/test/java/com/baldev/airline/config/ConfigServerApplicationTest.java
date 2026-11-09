package com.baldev.airline.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the server against the repository's config-repo/ and checks what clients get. */
@SpringBootTest(properties = {
        "CONFIG_REPO_LOCATION=file:../../config-repo/",
        "CONFIG_SERVER_PASSWORD=test-password"
})
@AutoConfigureMockMvc
class ConfigServerApplicationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void configurationRequiresCredentials() throws Exception {
        mvc.perform(get("/booking-service/default")).andExpect(status().isUnauthorized());
        mvc.perform(get("/booking-service/default").with(httpBasic("config", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void servesServiceAndSharedFilesMostSpecificFirst() throws Exception {
        mvc.perform(get("/booking-service/local").with(httpBasic("config", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("booking-service"))
                .andExpect(jsonPath("$.propertySources[0].name").value(org.hamcrest.Matchers.endsWith("booking-service-local.yaml")))
                .andExpect(jsonPath("$.propertySources[-1].name").value(org.hamcrest.Matchers.endsWith("application.yaml")));
    }

    @Test
    void probesArePublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }
}
