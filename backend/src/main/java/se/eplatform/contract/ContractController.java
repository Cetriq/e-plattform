package se.eplatform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.web.Audited;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service definitions (level 1), applications (level 2) and the concept
 * vocabulary. See ContractService for who may see and change what.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Tjänstekontrakt", description = "Nationella definitioner, lokala tillämpningar och begreppsmodellen")
public class ContractController {

    private final ContractService contracts;

    public ContractController(ContractService contracts) {
        this.contracts = contracts;
    }

    public record CreateDefinition(String key, String name) {}

    public record DefinitionDraft(JsonNode content, JsonNode legalBasis, LocalDate validFrom, LocalDate validTo) {}

    public record CreateApplication(UUID definitionId, String name) {}

    public record NewApplicationVersion(UUID definitionVersionId) {}

    public record ApplicationDraft(JsonNode content, LocalDate validFrom, LocalDate validTo) {}

    @ExceptionHandler(ContractService.InvalidDocument.class)
    public ResponseEntity<Map<String, Object>> invalid(ContractService.InvalidDocument e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("errors", e.errors()));
    }

    // ---------- Concepts ----------

    @Operation(summary = "Begreppsmodellen", description = "Begrepp med version, schema och dataklass.")
    @GetMapping("/concepts")
    public List<ContractService.Concept> concepts() {
        return contracts.concepts();
    }

    // ---------- Definitions ----------

    @Operation(summary = "Tjänstedefinitioner", description = "De egna definitionerna och alla publicerade.")
    @GetMapping("/definitions")
    public List<ContractService.Definition> definitions() {
        return contracts.definitions();
    }

    @GetMapping("/definitions/{id}")
    public ContractService.Definition definition(@PathVariable UUID id) {
        return contracts.definition(id);
    }

    @Operation(summary = "Ny definition", description = "Den aktiva organisationen blir förvaltare. Version 1 är ett tomt utkast.")
    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "DEFINITION")
    @PostMapping("/definitions")
    public ResponseEntity<ContractService.Definition> createDefinition(@RequestBody CreateDefinition request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contracts.createDefinition(request.key(), request.name()));
    }

    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "DEFINITION", idParam = "id")
    @PostMapping("/definitions/{id}/versions")
    public ResponseEntity<ContractService.Definition> newDefinitionVersion(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contracts.newDefinitionVersion(id));
    }

    @Operation(summary = "Ändra utkast", description = "Innehållet valideras mot eplatform.definition/1; fel ger 422.")
    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "DEFINITION", idParam = "id")
    @PutMapping("/definitions/{id}/versions/{version}")
    public ContractService.Definition updateDefinitionDraft(@PathVariable UUID id, @PathVariable int version,
                                                            @RequestBody DefinitionDraft draft) {
        return contracts.updateDefinitionDraft(id, version, draft.content(), draft.legalBasis(), draft.validFrom(),
                draft.validTo());
    }

    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "DEFINITION", idParam = "id")
    @PostMapping("/definitions/{id}/versions/{version}/publish")
    public ContractService.Definition publishDefinition(@PathVariable UUID id, @PathVariable int version) {
        return contracts.publishDefinition(id, version);
    }

    @Operation(summary = "Exportera", description = "Ett paket med dokumentet och dess hash, för att flytta definitionen till en annan miljö.")
    @GetMapping("/definitions/{id}/versions/{version}/export")
    public Map<String, Object> exportDefinition(@PathVariable UUID id, @PathVariable int version) {
        return contracts.exportDefinition(id, version);
    }

    @Operation(summary = "Importera", description = "Hashen kontrolleras; den aktiva organisationen blir förvaltare och innehållet får samma hash.")
    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "DEFINITION")
    @PostMapping("/definitions/import")
    public ResponseEntity<ContractService.Definition> importDefinition(@RequestBody JsonNode pkg) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contracts.importDefinition(pkg));
    }

    // ---------- Applications ----------

    @Operation(summary = "Tillämpningar", description = "Tillämpningar som organisationen äger eller har en roll i.")
    @GetMapping("/applications")
    public List<ContractService.Application> applications() {
        return contracts.applications();
    }

    @GetMapping("/applications/{id}")
    public ContractService.Application application(@PathVariable UUID id) {
        return contracts.application(id);
    }

    @Operation(summary = "Ny tillämpning", description = "Tillämpar definitionens senaste publicerade version. Organisationen äger tillämpningen och har alla roller tills de ändras.")
    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "APPLICATION")
    @PostMapping("/applications")
    public ResponseEntity<ContractService.Application> createApplication(@RequestBody CreateApplication request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contracts.createApplication(request.definitionId(), request.name()));
    }

    @Operation(summary = "Roller", description = "Ansvarig, beslutande myndighet, personuppgiftsansvarig och deltagare, var för sig. Exakt en ansvarig.")
    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "APPLICATION", idParam = "id")
    @PutMapping("/applications/{id}/roles")
    public ContractService.Application setRoles(@PathVariable UUID id, @RequestBody List<ContractService.Role> roles) {
        return contracts.setRoles(id, roles);
    }

    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "APPLICATION", idParam = "id")
    @PostMapping("/applications/{id}/versions")
    public ResponseEntity<ContractService.Application> newApplicationVersion(
            @PathVariable UUID id, @RequestBody(required = false) NewApplicationVersion request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contracts.newApplicationVersion(id, request == null ? null : request.definitionVersionId()));
    }

    @Operation(summary = "Ändra utkast", description = "Innehållet valideras mot eplatform.application/1; fel ger 422.")
    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "APPLICATION", idParam = "id")
    @PutMapping("/applications/{id}/versions/{version}")
    public ContractService.Application updateApplicationDraft(@PathVariable UUID id, @PathVariable int version,
                                                              @RequestBody ApplicationDraft draft) {
        return contracts.updateApplicationDraft(id, version, draft.content(), draft.validFrom(), draft.validTo());
    }

    @Audited(value = AuditAction.CONTRACT_CHANGE, entity = "APPLICATION", idParam = "id")
    @PostMapping("/applications/{id}/versions/{version}/publish")
    public ContractService.Application publishApplication(@PathVariable UUID id, @PathVariable int version) {
        return contracts.publishApplication(id, version);
    }
}
