package se.eplatform.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import se.eplatform.cases.repository.CaseRepository;
import se.eplatform.storage.domain.Attachment;
import se.eplatform.storage.repository.AttachmentRepository;
import se.eplatform.support.IntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Citizens may only see and change their own cases and files; staff may see
 * all cases; the acting user always comes from the token.
 */
class CaseAuthorizationTest extends IntegrationTest {

    @Autowired
    CaseRepository caseRepository;

    @Autowired
    AttachmentRepository attachmentRepository;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    String owner;
    String ownerId;
    String otherCitizen;
    String otherCitizenId;
    String manager;
    String caseId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode ownerLogin = newDemoCitizen(Map.of());
        owner = ownerLogin.get("token").asText();
        ownerId = ownerLogin.get("user").get("id").asText();

        JsonNode otherLogin = newDemoCitizen(Map.of());
        otherCitizen = otherLogin.get("token").asText();
        otherCitizenId = otherLogin.get("user").get("id").asText();

        manager = loginAs(MANAGER);
        caseId = createCase(owner);
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/v1/cases/" + caseId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/files/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mvc.perform(get("/api/v1/flows")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/flows/" + BUILDING_PERMIT_FLOW)).andExpect(status().isOk());
        // Health may be down in tests (no MinIO/mail), but it must not require a login
        mvc.perform(get("/actuator/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    void forgedOrMalformedTokensAreRejected() throws Exception {
        String token = owner;
        String forged = token.substring(0, token.lastIndexOf('.') + 1) + "AAAA" + token.substring(token.lastIndexOf('.') + 5);

        mvc.perform(as(forged, get("/api/v1/cases/" + caseId))).andExpect(status().isUnauthorized());
        mvc.perform(as("not-a-token", get("/api/v1/cases/" + caseId))).andExpect(status().isUnauthorized());
    }

    @Test
    void caseIsCreatedForTheLoggedInUserEvenIfAnotherUserIdIsSent() throws Exception {
        String response = mvc.perform(as(owner, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "flowId", BUILDING_PERMIT_FLOW,
                                "userId", otherCitizenId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID created = UUID.fromString(json.readTree(response).get("id").asText());

        assertThat(caseRepository.isOwnedBy(created, UUID.fromString(ownerId))).isTrue();
        assertThat(caseRepository.isOwnedBy(created, UUID.fromString(otherCitizenId))).isFalse();
    }

    @Test
    void ownerCanReadAndEditTheirCase() throws Exception {
        mvc.perform(as(owner, get("/api/v1/cases/" + caseId))).andExpect(status().isOk());
        mvc.perform(as(owner, get("/api/v1/cases/" + caseId + "/events"))).andExpect(status().isOk());
        mvc.perform(as(owner, put("/api/v1/cases/" + caseId + "/values"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(as(owner, get("/api/v1/cases/user/" + ownerId))).andExpect(status().isOk());
    }

    @Test
    void otherCitizenCannotSeeOrChangeTheCase() throws Exception {
        String reference = json.readTree(mvc.perform(as(owner, get("/api/v1/cases/" + caseId)))
                .andReturn().getResponse().getContentAsString()).get("referenceNumber").asText();

        mvc.perform(as(otherCitizen, get("/api/v1/cases/" + caseId))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, get("/api/v1/cases/ref/" + reference))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, get("/api/v1/cases/" + caseId + "/events"))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, get("/api/v1/cases/" + caseId + "/pdf/own"))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, put("/api/v1/cases/" + caseId + "/values"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, post("/api/v1/cases/" + caseId + "/submit"))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, delete("/api/v1/cases/" + caseId))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, get("/api/v1/cases/user/" + ownerId))).andExpect(status().isForbidden());
        mvc.perform(as(otherCitizen, get("/api/v1/cases/user/" + ownerId + "/drafts"))).andExpect(status().isForbidden());

        // The case is still there
        mvc.perform(as(owner, get("/api/v1/cases/" + caseId))).andExpect(status().isOk());
    }

    @Test
    void citizensCannotUseStaffEndpoints() throws Exception {
        mvc.perform(as(owner, get("/api/v1/cases"))).andExpect(status().isForbidden());
        mvc.perform(as(owner, get("/api/v1/cases/search").param("q", "E-"))).andExpect(status().isForbidden());
        mvc.perform(as(owner, get("/api/v1/cases/" + caseId + "/manager"))).andExpect(status().isForbidden());
        mvc.perform(as(owner, get("/api/v1/cases/" + caseId + "/pdf"))).andExpect(status().isForbidden());
        mvc.perform(as(owner, put("/api/v1/cases/" + caseId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("statusId", UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(as(owner, post("/api/v1/cases/" + caseId + "/messages/internal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", "hej"))))
                .andExpect(status().isForbidden());
        mvc.perform(as(owner, get("/api/v1/admin/users"))).andExpect(status().isForbidden());
        mvc.perform(as(owner, get("/api/v1/admin/statistics/overview"))).andExpect(status().isForbidden());
    }

    @Test
    void managerCanReadAndHandleButNotEditTheApplication() throws Exception {
        // Status can only change once the case is submitted
        jdbc.update("UPDATE cases SET submitted_at = NOW(), status_id = ? WHERE id = ?",
                UUID.fromString("00000000-0000-0000-0008-000000000002"), UUID.fromString(caseId));

        mvc.perform(as(manager, get("/api/v1/cases"))).andExpect(status().isOk());
        mvc.perform(as(manager, get("/api/v1/cases/" + caseId))).andExpect(status().isOk());
        String managerView = mvc.perform(as(manager, get("/api/v1/cases/" + caseId + "/manager")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String statusId = json.readTree(managerView).get("allowedTransitions").get(0).get("statusId").asText();
        mvc.perform(as(manager, put("/api/v1/cases/" + caseId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("statusId", statusId, "comment", "Granskas"))))
                .andExpect(status().isOk());

        // Changing the citizen's answers is the citizen's job
        mvc.perform(as(manager, put("/api/v1/cases/" + caseId + "/values"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // Managers are not administrators
        mvc.perform(as(manager, get("/api/v1/admin/users"))).andExpect(status().isForbidden());
    }

    @Test
    void adminCanUseAdminEndpoints() throws Exception {
        String admin = loginAs(ADMIN);
        mvc.perform(as(admin, get("/api/v1/admin/users"))).andExpect(status().isOk());
        mvc.perform(as(admin, get("/api/v1/admin/flows"))).andExpect(status().isOk());
    }

    @Test
    void filesAreOnlyVisibleToUploaderCaseOwnerAndStaff() throws Exception {
        Attachment attachment = new Attachment("ritning.pdf", "test-" + UUID.randomUUID(), "application/pdf",
                10L, "eplatform-attachments", UUID.fromString(ownerId));
        attachment.setCaseEntity(caseRepository.findById(UUID.fromString(caseId)).orElseThrow());
        String fileId = attachmentRepository.save(attachment).getId().toString();

        mvc.perform(as(owner, get("/api/v1/files/" + fileId))).andExpect(status().isOk());
        mvc.perform(as(manager, get("/api/v1/files/" + fileId))).andExpect(status().isOk());
        mvc.perform(as(otherCitizen, get("/api/v1/files/" + fileId))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, get("/api/v1/files/" + fileId + "/download"))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, get("/api/v1/files/case/" + caseId))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, delete("/api/v1/files/" + fileId))).andExpect(status().isNotFound());
        mvc.perform(as(otherCitizen, get("/api/v1/files/usage/" + ownerId))).andExpect(status().isForbidden());
    }

    @Test
    void errorsDoNotLeakInternals() throws Exception {
        mvc.perform(as(owner, get("/api/v1/cases/not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ogiltig begäran"));
    }
}
