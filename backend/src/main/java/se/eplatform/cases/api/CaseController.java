package se.eplatform.cases.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.cases.api.dto.CaseDTO;
import se.eplatform.cases.api.dto.ManagerCaseDTO;
import se.eplatform.cases.domain.Case;
import se.eplatform.cases.domain.ExternalMessage;
import se.eplatform.cases.domain.InternalMessage;
import se.eplatform.cases.service.CaseService;
import se.eplatform.pdf.PdfService;

import java.io.IOException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;
    private final PdfService pdfService;

    public CaseController(CaseService caseService, PdfService pdfService) {
        this.caseService = caseService;
        this.pdfService = pdfService;
    }

    /**
     * Get submitted cases with pagination.
     */
    @GetMapping
    public Page<CaseDTO> getCases(@PageableDefault(size = 20) Pageable pageable) {
        return caseService.getSubmittedCases(pageable)
                .map(CaseDTO::summary);
    }

    /**
     * Get a specific case with all values.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CaseDTO> getCase(@PathVariable UUID id) {
        return caseService.getCase(id)
                .map(CaseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get a specific case with manager-specific data (events, messages, statusDefinitions).
     */
    @GetMapping("/{id}/manager")
    public ResponseEntity<ManagerCaseDTO> getManagerCase(@PathVariable UUID id) {
        return caseService.getCaseForManager(id)
                .map(ManagerCaseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get case by reference number.
     */
    @GetMapping("/ref/{referenceNumber}")
    public ResponseEntity<CaseDTO> getCaseByReference(@PathVariable String referenceNumber) {
        return caseService.getCaseByReferenceNumber(referenceNumber)
                .map(CaseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get cases for a user.
     */
    @GetMapping("/user/{userId}")
    public Page<CaseDTO> getCasesForUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return caseService.getCasesForUser(userId, pageable)
                .map(CaseDTO::summary);
    }

    /**
     * Get draft cases for a user.
     */
    @GetMapping("/user/{userId}/drafts")
    public List<CaseDTO> getDraftsForUser(@PathVariable UUID userId) {
        return caseService.getDraftsForUser(userId).stream()
                .map(CaseDTO::summary)
                .toList();
    }

    /**
     * Create a new case.
     */
    @PostMapping
    public ResponseEntity<CaseDTO> createCase(@RequestBody CreateCaseRequest request) {
        Case newCase = caseService.createCase(request.flowId(), request.userId());
        CaseDTO dto = CaseDTO.from(newCase);
        return ResponseEntity
                .created(URI.create("/api/v1/cases/" + newCase.getId()))
                .body(dto);
    }

    /**
     * Update case values.
     */
    @PutMapping("/{id}/values")
    public ResponseEntity<CaseDTO> updateCaseValues(
            @PathVariable UUID id,
            @RequestBody Map<UUID, Object> values) {
        Case updated = caseService.updateCaseValues(id, values);
        return ResponseEntity.ok(CaseDTO.from(updated));
    }

    /**
     * Submit a case.
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<CaseDTO> submitCase(@PathVariable UUID id) {
        Case submitted = caseService.submitCase(id);
        return ResponseEntity.ok(CaseDTO.from(submitted));
    }

    /**
     * Change case status.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<CaseDTO> changeStatus(
            @PathVariable UUID id,
            @RequestBody ChangeStatusRequest request) {
        Case updated = caseService.changeStatus(id, request.statusId(), request.userId(), request.comment());
        return ResponseEntity.ok(CaseDTO.from(updated));
    }

    /**
     * Search cases.
     */
    @GetMapping("/search")
    public Page<CaseDTO> searchCases(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return caseService.searchCases(q, pageable)
                .map(CaseDTO::summary);
    }

    /**
     * Delete a draft case.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCase(@PathVariable UUID id) {
        caseService.deleteCase(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Add internal message (only visible to managers).
     */
    @PostMapping("/{id}/messages/internal")
    public ResponseEntity<ManagerCaseDTO.InternalMessageDTO> addInternalMessage(
            @PathVariable UUID id,
            @RequestBody MessageRequest request) {
        InternalMessage msg = caseService.addInternalMessage(id, request.userId(), request.message());
        return ResponseEntity.ok(ManagerCaseDTO.InternalMessageDTO.from(msg));
    }

    /**
     * Add external message (visible to citizen).
     */
    @PostMapping("/{id}/messages/external")
    public ResponseEntity<ManagerCaseDTO.ExternalMessageDTO> addExternalMessage(
            @PathVariable UUID id,
            @RequestBody MessageRequest request) {
        ExternalMessage msg = caseService.addExternalMessage(id, request.userId(), request.message(), true);
        return ResponseEntity.ok(ManagerCaseDTO.ExternalMessageDTO.from(msg));
    }

    /**
     * Generate PDF document for a case.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> generatePdf(@PathVariable UUID id) {
        return caseService.getCaseForManager(id)
                .map(caseEntity -> {
                    try {
                        byte[] pdfBytes = pdfService.generateCasePdf(caseEntity, caseEntity.getFlow());
                        String filename = "case-" + caseEntity.getReferenceNumber() + ".pdf";

                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .contentLength(pdfBytes.length)
                                .body(pdfBytes);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to generate PDF", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Request records

    public record CreateCaseRequest(UUID flowId, UUID userId) {}

    public record ChangeStatusRequest(UUID statusId, UUID userId, String comment) {}

    public record MessageRequest(UUID userId, String message) {}
}
