package se.eplatform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import se.eplatform.support.IntegrationTest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staff reach a case only with all four conditions of CaseAccessPolicy:
 * organisation, role, scope and clearance. Lists and direct reads agree.
 */
class TenantIsolationTest extends IntegrationTest {

    static final String GRANNKOMMUN_MANAGER = "handlaggare.grannkommun@example.com";
    static final String SCHOOL_TRANSPORT_FLOW = "00000000-0000-0000-0004-000000000011";
    static final String BYGGLOV_GROUP = "00000000-0000-0000-0000-000000000020";
    static final String MILJO_GROUP = "00000000-0000-0000-0000-000000000021";

    private static final String JSON_NULL_ASSIGNEE = "{\"userId\":null}";
    private static final String MESSAGE = "{\"message\":\"Hej\"}";

    /**
     * Every endpoint that takes the id of a case (or, under /files/case, a
     * case id), with a request a case worker might send. The isolation test
     * sends each of them across the organisation border; the inventory test
     * fails when an endpoint with an id is neither here nor in {@link #EXEMPT}.
     */
    static final Map<String, Function<String, MockHttpServletRequestBuilder>> CASE_ENDPOINTS = new LinkedHashMap<>();
    static {
        CASE_ENDPOINTS.put("GET /api/v1/cases/{id}", id -> get("/api/v1/cases/" + id));
        CASE_ENDPOINTS.put("GET /api/v1/cases/{id}/manager", id -> get("/api/v1/cases/" + id + "/manager"));
        CASE_ENDPOINTS.put("PUT /api/v1/cases/{id}/status", id -> put("/api/v1/cases/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"statusId\":\"" + UUID.randomUUID() + "\",\"comment\":\"x\"}"));
        CASE_ENDPOINTS.put("PUT /api/v1/cases/{id}/assignee", id -> put("/api/v1/cases/" + id + "/assignee")
                .contentType(MediaType.APPLICATION_JSON).content(JSON_NULL_ASSIGNEE));
        CASE_ENDPOINTS.put("GET /api/v1/cases/{id}/messages", id -> get("/api/v1/cases/" + id + "/messages"));
        CASE_ENDPOINTS.put("POST /api/v1/cases/{id}/messages", id -> post("/api/v1/cases/" + id + "/messages")
                .contentType(MediaType.APPLICATION_JSON).content(MESSAGE));
        CASE_ENDPOINTS.put("POST /api/v1/cases/{id}/messages/read", id -> post("/api/v1/cases/" + id + "/messages/read"));
        CASE_ENDPOINTS.put("POST /api/v1/cases/{id}/messages/internal", id -> post("/api/v1/cases/" + id + "/messages/internal")
                .contentType(MediaType.APPLICATION_JSON).content(MESSAGE));
        CASE_ENDPOINTS.put("POST /api/v1/cases/{id}/messages/external", id -> post("/api/v1/cases/" + id + "/messages/external")
                .contentType(MediaType.APPLICATION_JSON).content(MESSAGE));
        CASE_ENDPOINTS.put("GET /api/v1/cases/{id}/pdf", id -> get("/api/v1/cases/" + id + "/pdf"));
        CASE_ENDPOINTS.put("GET /api/v1/cases/{id}/pdf/own", id -> get("/api/v1/cases/" + id + "/pdf/own"));
        CASE_ENDPOINTS.put("GET /api/v1/cases/{id}/events", id -> get("/api/v1/cases/" + id + "/events"));
        CASE_ENDPOINTS.put("PUT /api/v1/cases/{id}/values", id -> put("/api/v1/cases/" + id + "/values")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        CASE_ENDPOINTS.put("POST /api/v1/cases/{id}/submit", id -> post("/api/v1/cases/" + id + "/submit"));
        CASE_ENDPOINTS.put("DELETE /api/v1/cases/{id}", id -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/cases/" + id));
        CASE_ENDPOINTS.put("GET /api/v1/files/case/{caseId}", id -> get("/api/v1/files/case/" + id));
    }

    /** Ids of Exempelkommun's administration objects, used across the border. */
    record AdminIds(String flow, String step, String query, String evaluator, String category, String type) {}

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /**
     * Every administration endpoint that takes an id, with a plausible request.
     * From another organisation each must answer 404, except the category list
     * of a service type, which answers 200 with only the caller's categories.
     */
    static final Map<String, Function<AdminIds, MockHttpServletRequestBuilder>> ADMIN_ENDPOINTS = new LinkedHashMap<>();
    static {
        var del = (java.util.function.Function<String, MockHttpServletRequestBuilder>)
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders::delete;
        String flows = "/api/v1/admin/flows/";
        ADMIN_ENDPOINTS.put("GET /api/v1/admin/flows/{id}", i -> get(flows + i.flow()));
        ADMIN_ENDPOINTS.put("PUT /api/v1/admin/flows/{id}", i -> json(put(flows + i.flow()), "{\"name\":\"Kapad\"}"));
        ADMIN_ENDPOINTS.put("DELETE /api/v1/admin/flows/{id}", i -> del.apply(flows + i.flow()));
        ADMIN_ENDPOINTS.put("POST /api/v1/admin/flows/{id}/publish", i -> post(flows + i.flow() + "/publish"));
        ADMIN_ENDPOINTS.put("POST /api/v1/admin/flows/{id}/archive", i -> post(flows + i.flow() + "/archive"));
        ADMIN_ENDPOINTS.put("POST /api/v1/admin/flows/{id}/duplicate", i -> post(flows + i.flow() + "/duplicate"));
        ADMIN_ENDPOINTS.put("POST /api/v1/admin/flows/{flowId}/steps",
                i -> json(post(flows + i.flow() + "/steps"), "{\"name\":\"Kapat steg\"}"));
        ADMIN_ENDPOINTS.put("PUT /api/v1/admin/flows/{flowId}/steps/reorder",
                i -> json(put(flows + i.flow() + "/steps/reorder"), "{\"ids\":[\"" + i.step() + "\"]}"));
        ADMIN_ENDPOINTS.put("PUT /api/v1/admin/flows/{flowId}/steps/{stepId}",
                i -> json(put(flows + i.flow() + "/steps/" + i.step()), "{\"name\":\"Kapat steg\"}"));
        ADMIN_ENDPOINTS.put("DELETE /api/v1/admin/flows/{flowId}/steps/{stepId}",
                i -> del.apply(flows + i.flow() + "/steps/" + i.step()));
        ADMIN_ENDPOINTS.put("POST /api/v1/admin/flows/{flowId}/steps/{stepId}/queries",
                i -> json(post(flows + i.flow() + "/steps/" + i.step() + "/queries"), "{\"name\":\"Kapad\",\"queryType\":\"TEXT\"}"));
        ADMIN_ENDPOINTS.put("PUT /api/v1/admin/flows/{flowId}/steps/{stepId}/queries/reorder",
                i -> json(put(flows + i.flow() + "/steps/" + i.step() + "/queries/reorder"), "{\"ids\":[\"" + i.query() + "\"]}"));
        ADMIN_ENDPOINTS.put("PUT /api/v1/admin/flows/{flowId}/steps/{stepId}/queries/{queryId}",
                i -> json(put(flows + i.flow() + "/steps/" + i.step() + "/queries/" + i.query()), "{\"name\":\"Kapad\"}"));
        ADMIN_ENDPOINTS.put("DELETE /api/v1/admin/flows/{flowId}/steps/{stepId}/queries/{queryId}",
                i -> del.apply(flows + i.flow() + "/steps/" + i.step() + "/queries/" + i.query()));
        ADMIN_ENDPOINTS.put("POST /api/v1/admin/flows/{flowId}/queries/{queryId}/evaluators",
                i -> json(post(flows + i.flow() + "/queries/" + i.query() + "/evaluators"),
                        "{\"evaluatorType\":\"IS_EMPTY\",\"condition\":{},\"targetQueryIds\":[\"" + i.query() + "\"],\"targetState\":\"HIDDEN\"}"));
        ADMIN_ENDPOINTS.put("PUT /api/v1/admin/flows/{flowId}/queries/{queryId}/evaluators/{evaluatorId}",
                i -> json(put(flows + i.flow() + "/queries/" + i.query() + "/evaluators/" + i.evaluator()),
                        "{\"evaluatorType\":\"IS_EMPTY\",\"condition\":{},\"targetQueryIds\":[\"" + i.query() + "\"],\"targetState\":\"HIDDEN\"}"));
        ADMIN_ENDPOINTS.put("DELETE /api/v1/admin/flows/{flowId}/queries/{queryId}/evaluators/{evaluatorId}",
                i -> del.apply(flows + i.flow() + "/queries/" + i.query() + "/evaluators/" + i.evaluator()));
        ADMIN_ENDPOINTS.put("GET /api/v1/admin/categories/{id}", i -> get("/api/v1/admin/categories/" + i.category()));
        ADMIN_ENDPOINTS.put("PUT /api/v1/admin/categories/{id}",
                i -> json(put("/api/v1/admin/categories/" + i.category()), "{\"name\":\"Kapad\"}"));
        ADMIN_ENDPOINTS.put("DELETE /api/v1/admin/categories/{id}", i -> del.apply("/api/v1/admin/categories/" + i.category()));
        ADMIN_ENDPOINTS.put("GET /api/v1/admin/flow-types/{id}", i -> get("/api/v1/admin/flow-types/" + i.type()));
        ADMIN_ENDPOINTS.put("PUT /api/v1/admin/flow-types/{id}",
                i -> json(put("/api/v1/admin/flow-types/" + i.type()), "{\"name\":\"Kapad\"}"));
        ADMIN_ENDPOINTS.put("DELETE /api/v1/admin/flow-types/{id}", i -> del.apply("/api/v1/admin/flow-types/" + i.type()));
        ADMIN_ENDPOINTS.put("GET /api/v1/admin/flow-types/{flowTypeId}/categories",
                i -> get("/api/v1/admin/flow-types/" + i.type() + "/categories"));
    }

    /**
     * Endpoints with an id that are covered elsewhere or are not about cases,
     * each with the reason. Shrinks as steg 1.3b (administration) and 1.3c
     * (traceability per organisation) land.
     */
    static final Map<String, String> EXEMPT = new LinkedHashMap<>();
    static {
        String security = "1.3c: traceability and data protection per organisation";
        String self = "the caller's own data only (FileController/CaseController check the user id)";
        String publicFlows = "published e-services are public";
        String referenced = "checked through CaseAccessPolicy on the case the reference or file points to (tested below)";
        EXEMPT.put("GET /api/v1/cases/ref/{referenceNumber}", referenced);
        EXEMPT.put("GET /api/v1/cases/user/{userId}", self);
        EXEMPT.put("GET /api/v1/cases/user/{userId}/drafts", self);
        EXEMPT.put("GET /api/v1/files/usage/{userId}", self);
        EXEMPT.put("GET /api/v1/files/{id}", referenced);
        EXEMPT.put("GET /api/v1/files/{id}/download", referenced);
        EXEMPT.put("GET /api/v1/files/{id}/url", referenced);
        EXEMPT.put("DELETE /api/v1/files/{id}", "only the uploader (FileController.requireUploadedBy)");
        EXEMPT.put("POST /api/v1/files/{id}/link", "only the uploader, and the owner of the case");
        EXEMPT.put("GET /api/v1/files/case/{caseId}/field/{queryDefinitionId}", referenced);
        for (String flow : List.of("GET /api/v1/flows/{id}", "GET /api/v1/flows/by-type/{typeId}",
                "GET /api/v1/flows/by-category/{categoryId}", "GET /api/v1/flows/family/{familyId}/versions")) {
            EXEMPT.put(flow, publicFlows);
        }
        EXEMPT.put("/api/v1/security/**", security);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    String citizen;
    String exempelCase;
    String grannCase;

    @BeforeEach
    void setUp() throws Exception {
        citizen = newDemoCitizen(Map.of()).get("token").asText();
        exempelCase = createCase(citizen);
        grannCase = createCaseOn(citizen, SCHOOL_TRANSPORT_FLOW);
        submit(exempelCase);
        submit(grannCase);
    }

    private String createCaseOn(String token, String flowId) throws Exception {
        return json.readTree(mvc.perform(as(token, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("flowId", flowId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void submit(String caseId) {
        jdbc.update("UPDATE cases c SET submitted_at = NOW(), status_id = s.id FROM status_definitions s " +
                    "WHERE c.id = ? AND s.flow_id = c.flow_id AND s.status_type = 'SUBMITTED'", UUID.fromString(caseId));
    }

    private int managerView(String token, String caseId) throws Exception {
        return mvc.perform(as(token, get("/api/v1/cases/" + caseId + "/manager"))).andReturn().getResponse().getStatus();
    }

    private Set<String> listed(String token) throws Exception {
        String body = mvc.perform(as(token, get("/api/v1/cases")).param("size", "1000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Set<String> ids = new HashSet<>();
        json.readTree(body).get("content").forEach(c -> ids.add(c.get("id").asText()));
        return ids;
    }

    // ---------- Organisation ----------

    @Test
    void aCaseWorkerInAnotherOrganisationGetsNotFoundEverywhere() throws Exception {
        String granne = loginAs(GRANNKOMMUN_MANAGER);
        for (var endpoint : CASE_ENDPOINTS.entrySet()) {
            int status = mvc.perform(as(granne, endpoint.getValue().apply(exempelCase)))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(endpoint.getKey()).isEqualTo(404);
        }
        mvc.perform(as(granne, get("/api/v1/cases/ref/" +
                        jdbc.queryForObject("SELECT reference_number FROM cases WHERE id = ?", String.class, UUID.fromString(exempelCase)))))
                .andExpect(status().isNotFound());

        // The same case worker reaches their own organisation's case
        assertThat(managerView(granne, grannCase)).isEqualTo(200);
        assertThat(managerView(loginAs(MANAGER), exempelCase)).isEqualTo(200);

        String security = loginAs("informationssakerhet@example.com");
        mvc.perform(as(security, get("/api/v1/security/audit"))
                        .param("action", "CASE_VIEW").param("entityId", exempelCase).param("outcome", "DENIED"))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void listsSearchAndStatisticsStayInTheOrganisation() throws Exception {
        String granne = loginAs(GRANNKOMMUN_MANAGER);
        Set<String> ids = listed(granne);
        assertThat(ids).contains(grannCase).doesNotContain(exempelCase);
        assertThat(listed(loginAs(MANAGER))).contains(exempelCase).doesNotContain(grannCase);

        String reference = jdbc.queryForObject("SELECT reference_number FROM cases WHERE id = ?", String.class,
                UUID.fromString(exempelCase));
        mvc.perform(as(granne, get("/api/v1/cases/search")).param("q", reference))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(loginAs(MANAGER), get("/api/v1/cases/search")).param("q", reference))
                .andExpect(jsonPath("$.totalElements").value(1));

        Integer grannCases = jdbc.queryForObject("SELECT COUNT(*) FROM cases WHERE organization_id = ?",
                Integer.class, UUID.fromString(OrganizationTest.GRANNKOMMUN));
        mvc.perform(as(loginAs("admin.grannkommun@example.com"), get("/api/v1/admin/statistics/overview")))
                .andExpect(jsonPath("$.totalCases").value(grannCases));

        // Only case workers of the case's own organisation can be assigned
        UUID grannManager = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, GRANNKOMMUN_MANAGER);
        mvc.perform(as(loginAs(MANAGER), put("/api/v1/cases/" + exempelCase + "/assignee"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + grannManager + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void citizensSeeTheirOwnCasesInEveryOrganisation() throws Exception {
        String citizenId = jdbc.queryForObject("SELECT created_by FROM cases WHERE id = ?", UUID.class,
                UUID.fromString(exempelCase)).toString();
        String body = mvc.perform(as(citizen, get("/api/v1/cases/user/" + citizenId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains(exempelCase).contains(grannCase);
        mvc.perform(as(citizen, get("/api/v1/cases/" + grannCase))).andExpect(status().isOk());

        // Staff no longer list a person's cases through this endpoint
        mvc.perform(as(loginAs(MANAGER), get("/api/v1/cases/user/" + citizenId))).andExpect(status().isForbidden());
    }

    // ---------- Scope and clearance within the organisation ----------

    /** A published copy of the building permit e-service with the given access scope. */
    private String flowWithScope(String scope) throws Exception {
        String copy = json.readTree(mvc.perform(as(loginAs(ADMIN), post("/api/v1/admin/flows/" + BUILDING_PERMIT_FLOW + "/duplicate")))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        jdbc.update("UPDATE flows SET status = 'PUBLISHED', enabled = true, access_scope = ? WHERE id = ?",
                scope, UUID.fromString(copy));
        return copy;
    }

    @Test
    void groupScopeLimitsTheCaseToItsHandlingGroup() throws Exception {
        String caseId = createCaseOn(citizen, flowWithScope("GROUP"));
        submit(caseId);
        String manager = loginAs(MANAGER); // member of Bygglovshandläggare

        jdbc.update("UPDATE cases SET handling_group_id = ? WHERE id = ?", UUID.fromString(MILJO_GROUP), UUID.fromString(caseId));
        assertThat(managerView(manager, caseId)).isEqualTo(404);
        assertThat(listed(manager)).doesNotContain(caseId);

        jdbc.update("UPDATE cases SET handling_group_id = ? WHERE id = ?", UUID.fromString(BYGGLOV_GROUP), UUID.fromString(caseId));
        assertThat(managerView(manager, caseId)).isEqualTo(200);
        assertThat(listed(manager)).contains(caseId);
    }

    @Test
    void assignedScopeLimitsTheCaseToTheAssignedCaseWorker() throws Exception {
        String caseId = createCaseOn(citizen, flowWithScope("ASSIGNED"));
        submit(caseId);
        String manager = loginAs(MANAGER);
        assertThat(managerView(manager, caseId)).isEqualTo(404);
        assertThat(managerView(loginAs(ADMIN), caseId)).isEqualTo(404);

        jdbc.update("UPDATE cases SET assigned_to = (SELECT id FROM users WHERE email = ?) WHERE id = ?",
                MANAGER, UUID.fromString(caseId));
        assertThat(managerView(manager, caseId)).isEqualTo(200);
        assertThat(listed(manager)).contains(caseId);
    }

    @Test
    void confidentialCasesNeedClearance() throws Exception {
        String manager = loginAs(MANAGER);
        jdbc.update("UPDATE cases SET confidentiality = 'CONFIDENTIAL' WHERE id = ?", UUID.fromString(exempelCase));
        assertThat(managerView(manager, exempelCase)).isEqualTo(404);
        assertThat(listed(manager)).doesNotContain(exempelCase);

        String clearance = "UPDATE organization_members SET clearance = ? WHERE user_id = (SELECT id FROM users WHERE email = ?)";
        jdbc.update(clearance, "CONFIDENTIAL", MANAGER);
        try {
            assertThat(managerView(manager, exempelCase)).isEqualTo(200);
            assertThat(listed(manager)).contains(exempelCase);
        } finally {
            jdbc.update(clearance, "NORMAL", MANAGER);
        }
        // The owner still reaches their case
        mvc.perform(as(citizen, get("/api/v1/cases/" + exempelCase))).andExpect(status().isOk());
    }

    // ---------- Lists and direct reads agree ----------

    @Test
    void aListNeverShowsACaseADirectReadRefuses() throws Exception {
        String groupFlow = flowWithScope("GROUP");
        String assignedFlow = flowWithScope("ASSIGNED");
        UUID managerId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, MANAGER);
        String[] flows = {BUILDING_PERMIT_FLOW, groupFlow, assignedFlow, SCHOOL_TRANSPORT_FLOW};
        String[] secrecy = {"NORMAL", "RESTRICTED", "CONFIDENTIAL"};
        String[] groups = {BYGGLOV_GROUP, MILJO_GROUP, null};
        String[] clearances = {"NORMAL", "RESTRICTED", "CONFIDENTIAL"};
        Random random = new Random(20261019);

        List<String> cases = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            String caseId = createCaseOn(citizen, flows[random.nextInt(flows.length)]);
            submit(caseId);
            String group = groups[random.nextInt(groups.length)];
            jdbc.update("UPDATE cases SET confidentiality = ?, handling_group_id = ?, assigned_to = ? WHERE id = ?",
                    secrecy[random.nextInt(secrecy.length)], group == null ? null : UUID.fromString(group),
                    random.nextBoolean() ? managerId : null, UUID.fromString(caseId));
            cases.add(caseId);
        }

        String clearance = "UPDATE organization_members SET clearance = ? WHERE user_id = ?";
        try {
            for (String level : clearances) {
                jdbc.update(clearance, level, managerId);
                String manager = loginAs(MANAGER);
                Set<String> listed = listed(manager);
                for (String caseId : cases) {
                    boolean readable = managerView(manager, caseId) == 200;
                    assertThat(listed.contains(caseId)).as("case %s at clearance %s", caseId, level).isEqualTo(readable);
                }
            }
        } finally {
            jdbc.update(clearance, "NORMAL", managerId);
        }
    }


    // ---------- Administration ----------

    private AdminIds exempelAdminIds() throws Exception {
        String type = json.readTree(mvc.perform(as(loginAs(ADMIN), post("/api/v1/admin/flow-types"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Exempelkommuns egen typ\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        Map<String, Object> evaluator = jdbc.queryForMap("""
                SELECT e.id, e.query_id, q.step_id FROM evaluator_definitions e
                JOIN query_definitions q ON q.id = e.query_id JOIN steps s ON s.id = q.step_id
                WHERE s.flow_id = ? LIMIT 1""", UUID.fromString(BUILDING_PERMIT_FLOW));
        return new AdminIds(BUILDING_PERMIT_FLOW, evaluator.get("step_id").toString(), evaluator.get("query_id").toString(),
                evaluator.get("id").toString(), "00000000-0000-0000-0002-000000000001", type);
    }

    @Test
    void anAdministratorInAnotherOrganisationGetsNotFoundEverywhere() throws Exception {
        AdminIds ids = exempelAdminIds();
        String flowBefore = jdbc.queryForObject("SELECT name || status || version FROM flows WHERE id = ?", String.class,
                UUID.fromString(BUILDING_PERMIT_FLOW));
        String granneAdmin = loginAs("admin.grannkommun@example.com");

        for (var endpoint : ADMIN_ENDPOINTS.entrySet()) {
            var response = mvc.perform(as(granneAdmin, endpoint.getValue().apply(ids))).andReturn().getResponse();
            if (endpoint.getKey().endsWith("/categories") && endpoint.getKey().startsWith("GET /api/v1/admin/flow-types")) {
                assertThat(response.getStatus()).as(endpoint.getKey()).isIn(200, 404);
                assertThat(response.getContentAsString()).as(endpoint.getKey()).doesNotContain(ids.category());
            } else {
                assertThat(response.getStatus()).as(endpoint.getKey()).isEqualTo(404);
            }
        }

        // Nothing changed
        assertThat(jdbc.queryForObject("SELECT name || status || version FROM flows WHERE id = ?", String.class,
                UUID.fromString(BUILDING_PERMIT_FLOW))).isEqualTo(flowBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flow_types WHERE id = ?", Integer.class, UUID.fromString(ids.type())))
                .isOne();
        mvc.perform(as(loginAs(ADMIN), get("/api/v1/admin/flows/" + BUILDING_PERMIT_FLOW))).andExpect(status().isOk());
    }

    @Test
    void administrationListsShowOnlyTheOrganisation() throws Exception {
        String granneAdmin = loginAs("admin.grannkommun@example.com");
        String flows = mvc.perform(as(granneAdmin, get("/api/v1/admin/flows")).param("size", "200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(flows).contains(SCHOOL_TRANSPORT_FLOW).doesNotContain(BUILDING_PERMIT_FLOW);

        String categories = mvc.perform(as(granneAdmin, get("/api/v1/admin/categories")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(categories).contains("Skolskjuts").doesNotContain("Bygglov");

        String types = mvc.perform(as(granneAdmin, get("/api/v1/admin/flow-types")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(types).contains("Skola & Förskola").doesNotContain("Bygglov");

        String users = mvc.perform(as(granneAdmin, get("/api/v1/admin/users")).param("size", "200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(users).contains(GRANNKOMMUN_MANAGER).doesNotContain(MANAGER + "\"").doesNotContain("demo.example.com");
    }

    @Test
    void sharedServiceTypesCannotBeChangedByOneOrganisation() throws Exception {
        String shared = "00000000-0000-0000-0001-000000000004";
        mvc.perform(as(loginAs("admin.grannkommun@example.com"), put("/api/v1/admin/flow-types/" + shared))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ändrad\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(loginAs(ADMIN), org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/flow-types/" + shared)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT name FROM flow_types WHERE id = ?", String.class, UUID.fromString(shared)))
                .isEqualTo("Skola & Förskola");
    }

    // ---------- Inventory ----------

    @Test
    void everyEndpointWithAnIdIsCoveredOrExplicitlyExempt() {
        Set<String> uncovered = new TreeSet<>();
        Set<String> seen = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            if (info.getPathPatternsCondition() == null) return;
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.contains("{")) continue;
                Set<String> methods = new TreeSet<>();
                info.getMethodsCondition().getMethods().forEach(m -> methods.add(m.name()));
                if (methods.isEmpty()) methods.add("ANY");
                for (String method : methods) {
                    String key = method + " " + pattern;
                    seen.add(key);
                    boolean exempt = EXEMPT.containsKey(key) || EXEMPT.keySet().stream()
                            .filter(k -> k.endsWith("/**"))
                            .anyMatch(k -> pattern.startsWith(k.substring(0, k.length() - 3)));
                    if (!CASE_ENDPOINTS.containsKey(key) && !ADMIN_ENDPOINTS.containsKey(key) && !exempt) {
                        uncovered.add(key);
                    }
                }
            }
        });
        assertThat(uncovered)
                .as("Endpoints with an id must be in TenantIsolationTest.CASE_ENDPOINTS or EXEMPT")
                .isEmpty();
        // The lists must describe endpoints that exist, or the test proves nothing
        assertThat(seen).containsAll(CASE_ENDPOINTS.keySet());
        assertThat(seen).containsAll(ADMIN_ENDPOINTS.keySet());
        assertThat(seen).containsAll(EXEMPT.keySet().stream().filter(k -> !k.endsWith("/**")).toList());

    }
}
