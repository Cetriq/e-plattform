package se.eplatform.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import se.eplatform.support.IntegrationTest;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With files in Vercel Blob the backend only decides who may upload and read
 * files and records them; the bytes never pass through it.
 */
@TestPropertySource(properties = "eplatform.storage.provider=vercel-blob")
class BlobStorageTest extends IntegrationTest {

    String owner;
    String otherCitizen;
    String caseId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newDemoCitizen(Map.of()).get("token").asText();
        otherCitizen = newDemoCitizen(Map.of()).get("token").asText();
        caseId = createCase(owner);
    }

    @Test
    void onlyTheCaseOwnerMayUpload() throws Exception {
        mvc.perform(as(owner, post("/api/v1/files/upload-permission"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("caseId", caseId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pathnamePrefix").value("cases/" + caseId + "/"));

        mvc.perform(as(otherCitizen, post("/api/v1/files/upload-permission"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("caseId", caseId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void registeredFileIsServedThroughTheBlobRouteToAuthorizedUsersOnly() throws Exception {
        String response = mvc.perform(as(owner, post("/api/v1/files/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(registration("cases/" + caseId + "/ritning-x1.pdf"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode attachment = json.readTree(response);
        String id = attachment.get("id").asText();

        mvc.perform(as(owner, get("/api/v1/files/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("/api/blob/files/" + id))
                .andExpect(jsonPath("$.blobPathname").value("cases/" + caseId + "/ritning-x1.pdf"));
        mvc.perform(as(otherCitizen, get("/api/v1/files/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(owner, get("/api/v1/files/" + id + "/download"))).andExpect(status().isConflict());
    }

    @Test
    void registrationIsRejectedForOtherCasesAndDisallowedFiles() throws Exception {
        mvc.perform(as(otherCitizen, post("/api/v1/files/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(registration("cases/" + caseId + "/x.pdf"))))
                .andExpect(status().isNotFound());

        mvc.perform(as(owner, post("/api/v1/files/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(registration("cases/someone-else/x.pdf"))))
                .andExpect(status().isBadRequest());

        Map<String, Object> exe = registration("cases/" + caseId + "/x.exe");
        exe.put("contentType", "application/x-msdownload");
        mvc.perform(as(owner, post("/api/v1/files/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(exe)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadsThroughTheBackendAreRefused() throws Exception {
        mvc.perform(as(owner, multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("caseId", caseId)))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> registration(String pathname) {
        Map<String, Object> body = new HashMap<>();
        body.put("pathname", pathname);
        body.put("originalFilename", "ritning.pdf");
        body.put("contentType", "application/pdf");
        body.put("fileSize", 1234);
        body.put("caseId", caseId);
        return body;
    }
}
