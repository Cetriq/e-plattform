package se.eplatform.security;

import org.junit.jupiter.api.Test;
import se.eplatform.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Extending a session before it runs out (the warning shown for WCAG 2.2.1).
 */
class SessionRefreshTest extends IntegrationTest {

    @Test
    void validTokenIsExchangedForANewOne() throws Exception {
        String token = loginAs(CITIZEN);
        String body = mvc.perform(as(token, post("/api/v1/public/auth/refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(CITIZEN))
                .andReturn().getResponse().getContentAsString();
        String fresh = json.readTree(body).get("token").asText();
        assertThat(fresh).isNotBlank();

        mvc.perform(as(fresh, get("/api/v1/public/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CITIZEN));
    }

    @Test
    void invalidOrMissingTokenCannotBeRefreshed() throws Exception {
        mvc.perform(post("/api/v1/public/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(as("not-a-token", post("/api/v1/public/auth/refresh"))).andExpect(status().isUnauthorized());
    }
}
