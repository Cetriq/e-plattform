package se.eplatform.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import se.eplatform.common.security.TokenService;
import se.eplatform.support.IntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staff work for one organisation at a time; the token says which, and the
 * roles that apply are the ones they have there.
 */
class OrganizationTest extends IntegrationTest {

    static final String EXEMPELKOMMUN = "00000000-0000-0000-0000-000000000010";
    static final String GRANNKOMMUN = "00000000-0000-0000-0000-000000000011";
    static final String GRANNKOMMUN_MANAGER = "handlaggare.grannkommun@example.com";
    static final String SCHOOL_TRANSPORT_FLOW = "00000000-0000-0000-0004-000000000011";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenService tokens;

    private JsonNode me(String token) throws Exception {
        return json.readTree(mvc.perform(as(token, get("/api/v1/public/auth/me")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String switchTo(String token, String organizationId, int expectedStatus) throws Exception {
        return mvc.perform(as(token, post("/api/v1/public/auth/organization"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("organizationId", organizationId))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void staffWorkForTheirOrganisationWithTheRolesTheyHaveThere() throws Exception {
        JsonNode exempel = me(loginAs(MANAGER));
        assertThat(exempel.get("activeOrganization").get("slug").asText()).isEqualTo("exempelkommun");
        assertThat(exempel.get("roles").toString()).contains("MANAGER");

        JsonNode granne = me(loginAs(GRANNKOMMUN_MANAGER));
        assertThat(granne.get("activeOrganization").get("slug").asText()).isEqualTo("grannkommun");
        assertThat(granne.get("memberships").size()).isEqualTo(1);
    }

    @Test
    void citizensHaveNoActiveOrganisation() throws Exception {
        JsonNode citizen = me(newDemoCitizen(Map.of()).get("token").asText());
        // Null fields are left out of responses (spring.jackson.default-property-inclusion: non_null)
        assertThat(citizen.path("activeOrganization").isMissingNode() || citizen.get("activeOrganization").isNull()).isTrue();
        assertThat(citizen.get("memberships").size()).isZero();
        assertThat(citizen.get("roles").toString()).contains("USER");
    }

    @Test
    void switchingToAnOrganisationYouAreNotAMemberOfIsDeniedAndLogged() throws Exception {
        String manager = loginAs(MANAGER);
        switchTo(manager, GRANNKOMMUN, 403);

        String security = loginAs("informationssakerhet@example.com");
        mvc.perform(as(security, get("/api/v1/security/audit"))
                        .param("action", "ORGANIZATION_SWITCH").param("entityId", GRANNKOMMUN))
                .andExpect(jsonPath("$.content[0].outcome").value("DENIED"));
    }

    @Test
    void aMemberOfTwoOrganisationsSwitchesAndGetsTheRolesOfTheOtherOne() throws Exception {
        // Make the Grannkommun case worker a security officer in Exempelkommun too
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, GRANNKOMMUN_MANAGER);
        UUID memberId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_members (id, user_id, organization_id) VALUES (?, ?, ?)",
                memberId, userId, UUID.fromString(EXEMPELKOMMUN));
        jdbc.update("INSERT INTO organization_member_roles (member_id, role_id) " +
                    "SELECT ?, id FROM roles WHERE name = 'SECURITY_OFFICER'", memberId);
        try {
            String granne = loginAs(GRANNKOMMUN_MANAGER);
            mvc.perform(as(granne, get("/api/v1/security/audit"))).andExpect(status().isForbidden());

            String switched = json.readTree(switchTo(granne, EXEMPELKOMMUN, 200)).get("token").asText();
            JsonNode info = me(switched);
            assertThat(info.get("activeOrganization").get("id").asText()).isEqualTo(EXEMPELKOMMUN);
            assertThat(info.get("roles").toString()).contains("SECURITY_OFFICER").doesNotContain("MANAGER");
            assertThat(info.get("memberships").size()).isEqualTo(2);
            mvc.perform(as(switched, get("/api/v1/security/audit"))).andExpect(status().isOk());

            // Once the membership is gone, a token naming that organisation stops working
            jdbc.update("DELETE FROM organization_members WHERE id = ?", memberId);
            mvc.perform(as(switched, get("/api/v1/public/auth/me"))).andExpect(status().isUnauthorized());
            mvc.perform(as(switched, get("/api/v1/security/audit"))).andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("DELETE FROM organization_members WHERE id = ?", memberId);
        }
    }

    @Test
    void aTokenNamingAnotherOrganisationIsRejected() throws Exception {
        UUID managerId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, MANAGER);
        String forged = tokens.issue(managerId, UUID.fromString(GRANNKOMMUN));
        mvc.perform(as(forged, get("/api/v1/public/auth/me"))).andExpect(status().isUnauthorized());
        mvc.perform(as(forged, get("/api/v1/cases"))).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutOrganisationGivesOnlyGlobalRolesUntilRefreshed() throws Exception {
        UUID managerId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, MANAGER);
        String old = tokens.issue(managerId);
        assertThat(me(old).get("roles").toString()).doesNotContain("MANAGER");
        mvc.perform(as(old, get("/api/v1/cases"))).andExpect(status().isForbidden());

        String refreshed = json.readTree(mvc.perform(as(old, post("/api/v1/public/auth/refresh")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        assertThat(me(refreshed).get("activeOrganization").get("id").asText()).isEqualTo(EXEMPELKOMMUN);
    }

    @Test
    void theCatalogueCanBeLimitedToOneOrganisation() throws Exception {
        mvc.perform(get("/api/v1/public/organizations"))
                .andExpect(jsonPath("$[?(@.slug == 'exempelkommun')]").exists())
                .andExpect(jsonPath("$[?(@.slug == 'grannkommun')]").exists());

        mvc.perform(get("/api/v1/flows").param("organization", "grannkommun"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(SCHOOL_TRANSPORT_FLOW))
                .andExpect(jsonPath("$[0].organizationId").value(GRANNKOMMUN));
        mvc.perform(get("/api/v1/flows").param("organization", "exempelkommun"))
                .andExpect(jsonPath("$[?(@.id == '" + SCHOOL_TRANSPORT_FLOW + "')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == '" + BUILDING_PERMIT_FLOW + "')]").exists());
        mvc.perform(get("/api/v1/flows").param("organization", "finns-inte"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/flows"))
                .andExpect(jsonPath("$[?(@.id == '" + SCHOOL_TRANSPORT_FLOW + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + BUILDING_PERMIT_FLOW + "')]").exists());
    }

    @Test
    void casesAndNewEServicesBelongToTheRightOrganisation() throws Exception {
        String citizen = newDemoCitizen(Map.of()).get("token").asText();
        String caseId = json.readTree(mvc.perform(as(citizen, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("flowId", SCHOOL_TRANSPORT_FLOW))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        assertThat(jdbc.queryForObject("SELECT organization_id FROM cases WHERE id = ?", UUID.class, UUID.fromString(caseId)))
                .isEqualTo(UUID.fromString(GRANNKOMMUN));

        String admin = loginAs("admin.grannkommun@example.com");
        String flowId = json.readTree(mvc.perform(as(admin, post("/api/v1/admin/flows"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Ansökan om parkeringstillstånd"))))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        assertThat(jdbc.queryForObject("SELECT organization_id FROM flows WHERE id = ?", UUID.class, UUID.fromString(flowId)))
                .isEqualTo(UUID.fromString(GRANNKOMMUN));
    }
}
