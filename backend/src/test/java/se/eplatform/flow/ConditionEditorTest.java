package se.eplatform.flow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import se.eplatform.support.IntegrationTest;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admins configure conditional fields: "when field A has value X, show
 * (or require, or hide) fields B and C".
 */
class ConditionEditorTest extends IntegrationTest {

    String admin;
    String flowId;
    String sourceId;
    String targetId;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAs(ADMIN);
        // Work on a copy so the seeded flow stays as it is
        String copy = mvc.perform(as(admin, post("/api/v1/admin/flows/" + BUILDING_PERMIT_FLOW + "/duplicate")))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        flowId = json.readTree(copy).get("id").asText();

        JsonNode flow = json.readTree(mvc.perform(as(admin, get("/api/v1/admin/flows/" + flowId)))
                .andReturn().getResponse().getContentAsString());
        JsonNode queries = flow.get("steps").get(1).get("queries");
        sourceId = queries.get(0).get("id").asText();
        targetId = queries.get(1).get("id").asText();
    }

    @Test
    void showingAFieldMakesItStartHiddenUntilTheConditionIsRemoved() throws Exception {
        String created = mvc.perform(as(admin, post(evaluatorsPath()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "evaluatorType", "IS_NOT_EMPTY",
                                "condition", Map.of(),
                                "targetQueryIds", List.of(targetId),
                                "targetState", "VISIBLE_REQUIRED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetState").value("VISIBLE_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        String evaluatorId = json.readTree(created).get("id").asText();

        mvc.perform(as(admin, get("/api/v1/admin/flows/" + flowId)))
                .andExpect(jsonPath("$.steps[1].queries[0].evaluators[0].evaluatorType").value("IS_NOT_EMPTY"))
                .andExpect(jsonPath("$.steps[1].queries[1].defaultState").value("HIDDEN"));

        mvc.perform(as(admin, delete(evaluatorsPath() + "/" + evaluatorId)))
                .andExpect(status().isNoContent());

        mvc.perform(as(admin, get("/api/v1/admin/flows/" + flowId)))
                .andExpect(jsonPath("$.steps[1].queries[0].evaluators.length()").value(0))
                .andExpect(jsonPath("$.steps[1].queries[1].defaultState").value("VISIBLE"));
    }

    @Test
    void invalidConditionsAreRejected() throws Exception {
        // A field can't control itself
        mvc.perform(as(admin, post(evaluatorsPath()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "evaluatorType", "IS_EMPTY",
                                "targetQueryIds", List.of(sourceId),
                                "targetState", "HIDDEN"))))
                .andExpect(status().isBadRequest());

        // Targets must be in the same flow
        mvc.perform(as(admin, post(evaluatorsPath()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "evaluatorType", "IS_EMPTY",
                                "targetQueryIds", List.of("00000000-0000-0000-0006-000000000004"),
                                "targetState", "HIDDEN"))))
                .andExpect(status().isBadRequest());

        // Custom code conditions can't be configured here
        mvc.perform(as(admin, post(evaluatorsPath()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "evaluatorType", "CUSTOM",
                                "targetQueryIds", List.of(targetId),
                                "targetState", "HIDDEN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsCanEditConditions() throws Exception {
        String manager = loginAs(MANAGER);
        mvc.perform(as(manager, post(evaluatorsPath()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "evaluatorType", "IS_EMPTY",
                                "targetQueryIds", List.of(targetId),
                                "targetState", "HIDDEN"))))
                .andExpect(status().isForbidden());
    }

    private String evaluatorsPath() {
        return "/api/v1/admin/flows/" + flowId + "/queries/" + sourceId + "/evaluators";
    }
}
