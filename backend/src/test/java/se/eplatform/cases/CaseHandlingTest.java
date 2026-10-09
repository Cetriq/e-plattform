package se.eplatform.cases;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import se.eplatform.support.IntegrationTest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Case handling: allowed status changes, messages between citizen and
 * handläggare, internal notes and assignment.
 */
class CaseHandlingTest extends IntegrationTest {

    // Statuses of the seeded building permit flow
    static final String SUBMITTED = "00000000-0000-0000-0008-000000000002";
    static final String IN_REVIEW = "00000000-0000-0000-0008-000000000003";
    static final String NEEDS_COMPLETION = "00000000-0000-0000-0008-000000000004";
    static final String APPROVED = "00000000-0000-0000-0008-000000000005";

    @Autowired
    JdbcTemplate jdbc;

    String citizen;
    String citizenId;
    String manager;
    String managerId;
    String caseId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode login = newDemoCitizen(Map.of());
        citizen = login.get("token").asText();
        citizenId = login.get("user").get("id").asText();
        JsonNode managerLogin = login(Map.of("email", MANAGER));
        manager = managerLogin.get("token").asText();
        managerId = managerLogin.get("user").get("id").asText();
        caseId = createCase(citizen);
        markSubmitted(caseId);
    }

    /** Submitting through the API requires every required answer; tests only need the state. */
    private void markSubmitted(String id) {
        jdbc.update("UPDATE cases SET submitted_at = NOW(), status_id = ? WHERE id = ?",
                UUID.fromString(SUBMITTED), UUID.fromString(id));
    }

    @Test
    void onlyConfiguredStatusChangesAreAllowed() throws Exception {
        mvc.perform(as(manager, get("/api/v1/cases/" + caseId + "/manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions.length()").value(2))
                .andExpect(jsonPath("$.allowedTransitions[?(@.statusId == '" + IN_REVIEW + "')].requiresComment").value(false))
                .andExpect(jsonPath("$.allowedTransitions[?(@.statusId == '" + NEEDS_COMPLETION + "')].requiresComment").value(true));

        // Inskickad -> Godkänd skips the review
        mvc.perform(changeStatus(APPROVED, "Bra")).andExpect(status().isConflict());
        // Komplettering begärd needs a comment
        mvc.perform(changeStatus(NEEDS_COMPLETION, null)).andExpect(status().isBadRequest());
        mvc.perform(changeStatus(NEEDS_COMPLETION, "Ritningen saknar mått")).andExpect(status().isOk());
        mvc.perform(changeStatus(IN_REVIEW, null)).andExpect(status().isOk());
    }

    @Test
    void draftsCannotChangeStatus() throws Exception {
        String draft = createCase(citizen);
        mvc.perform(as(manager, put("/api/v1/cases/" + draft + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("statusId", IN_REVIEW))))
                .andExpect(status().isConflict());
    }

    @Test
    void citizenAndManagerExchangeMessages() throws Exception {
        mvc.perform(as(manager, post("/api/v1/cases/" + caseId + "/messages/external"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", "Kan du skicka en ny situationsplan?"))))
                .andExpect(status().isOk());

        // The citizen sees one unread message, from "Handläggare"
        mvc.perform(as(citizen, get("/api/v1/cases/user/" + citizenId)))
                .andExpect(jsonPath("$.content[0].unreadMessages").value(1));
        mvc.perform(as(citizen, get("/api/v1/cases/" + caseId + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].authorName").value("Handläggare"))
                .andExpect(jsonPath("$[0].fromManager").value(true));

        mvc.perform(as(citizen, post("/api/v1/cases/" + caseId + "/messages/read")))
                .andExpect(status().isNoContent());
        mvc.perform(as(citizen, get("/api/v1/cases/user/" + citizenId)))
                .andExpect(jsonPath("$.content[0].unreadMessages").value(0));

        // The reply shows up for the manager as unread
        mvc.perform(as(citizen, post("/api/v1/cases/" + caseId + "/messages"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", "Här kommer den!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromManager").value(false));
        mvc.perform(as(manager, get("/api/v1/cases").param("q", "")))
                .andExpect(jsonPath("$.content[?(@.id == '" + caseId + "')].unreadMessages").value(1));
        mvc.perform(as(manager, get("/api/v1/cases/" + caseId + "/messages")))
                .andExpect(jsonPath("$[1].authorName").isNotEmpty())
                .andExpect(jsonPath("$[1].message").value("Här kommer den!"));
    }

    @Test
    void messagesAreLimitedToTheOwnerAndSubmittedCases() throws Exception {
        String otherCitizen = newDemoCitizen(Map.of()).get("token").asText();
        mvc.perform(as(otherCitizen, post("/api/v1/cases/" + caseId + "/messages"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", "Hej"))))
                .andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, get("/api/v1/cases/" + caseId + "/messages")))
                .andExpect(status().isNotFound());

        String draft = createCase(citizen);
        mvc.perform(as(citizen, post("/api/v1/cases/" + draft + "/messages"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", "Hej"))))
                .andExpect(status().isConflict());

        mvc.perform(as(citizen, post("/api/v1/cases/" + caseId + "/messages"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalNotesAreHiddenFromTheCitizen() throws Exception {
        mvc.perform(as(manager, post("/api/v1/cases/" + caseId + "/messages/internal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", "Grannen har överklagat förut"))))
                .andExpect(status().isOk());
        mvc.perform(as(manager, put("/api/v1/cases/" + caseId + "/assignee"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", managerId))))
                .andExpect(status().isOk());

        String events = mvc.perform(as(citizen, get("/api/v1/cases/" + caseId + "/events")))
                .andReturn().getResponse().getContentAsString();
        String messages = mvc.perform(as(citizen, get("/api/v1/cases/" + caseId + "/messages")))
                .andReturn().getResponse().getContentAsString();
        String caseView = mvc.perform(as(citizen, get("/api/v1/cases/" + caseId)))
                .andReturn().getResponse().getContentAsString();

        assertThat(events).doesNotContain("Intern anteckning").doesNotContain("Tilldelat");
        assertThat(messages).doesNotContain("Grannen");
        // Null fields are left out of the JSON
        assertThat(json.readTree(caseView).has("assignedToName")).isFalse();

        mvc.perform(as(manager, get("/api/v1/cases/" + caseId + "/manager")))
                .andExpect(jsonPath("$.internalMessages[0].message").value("Grannen har överklagat förut"))
                .andExpect(jsonPath("$.assignedToId").value(managerId));
    }

    @Test
    void casesCanBeAssignedAndFiltered() throws Exception {
        mvc.perform(as(manager, get("/api/v1/cases/assignees")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + managerId + "')]").exists());

        mvc.perform(as(manager, get("/api/v1/cases").param("assignee", "unassigned")))
                .andExpect(jsonPath("$.content[?(@.id == '" + caseId + "')]").exists());

        mvc.perform(as(manager, put("/api/v1/cases/" + caseId + "/assignee"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", managerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToName").isNotEmpty());

        mvc.perform(as(manager, get("/api/v1/cases").param("assignee", "mine")))
                .andExpect(jsonPath("$.content[?(@.id == '" + caseId + "')]").exists());
        mvc.perform(as(manager, get("/api/v1/cases").param("assignee", "unassigned")))
                .andExpect(jsonPath("$.content[?(@.id == '" + caseId + "')]").doesNotExist());

        // Citizens can't be assigned, and can't assign
        mvc.perform(as(manager, put("/api/v1/cases/" + caseId + "/assignee"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", citizenId))))
                .andExpect(status().isBadRequest());
        mvc.perform(as(citizen, put("/api/v1/cases/" + caseId + "/assignee"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", managerId))))
                .andExpect(status().isForbidden());

        // Unassign
        Map<String, Object> clear = new HashMap<>();
        clear.put("userId", null);
        mvc.perform(as(manager, put("/api/v1/cases/" + caseId + "/assignee"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(clear)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").doesNotExist());
    }

    private MockHttpServletRequestBuilder changeStatus(String statusId, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("statusId", statusId);
        body.put("comment", comment);
        return as(manager, put("/api/v1/cases/" + caseId + "/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }
}
