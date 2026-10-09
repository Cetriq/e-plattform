package se.eplatform.cases.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import se.eplatform.auth.dto.AuthResponse.UserInfo;
import se.eplatform.cases.api.dto.CaseDTO;
import se.eplatform.cases.api.dto.CaseEventDTO;
import se.eplatform.cases.api.dto.CaseMessageDTO;
import se.eplatform.cases.api.dto.ManagerCaseDTO;
import se.eplatform.cases.domain.Case;
import se.eplatform.cases.domain.ExternalMessage;
import se.eplatform.cases.domain.InternalMessage;
import se.eplatform.cases.service.CaseAccessService;
import se.eplatform.cases.service.CaseService;
import se.eplatform.common.security.CurrentUser;
import se.eplatform.pdf.PdfService;

import java.io.IOException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cases")
@Tag(name = "Cases", description = """
    Ärenden och ansökningar.

    Ett **Case** (ärende) skapas när en medborgare påbörjar en ansökan via en e-tjänst (Flow).
    Ärendet innehåller alla svar (QueryInstances) och går genom olika statusar under handläggning.

    **Behörighet:** medborgare ser och ändrar bara egna ärenden. Handläggare och administratörer
    (MANAGER, ADMIN) ser alla ärenden och kan ändra status och skicka meddelanden.
    Den agerande användaren tas alltid från inloggningen.
    """)
public class CaseController {

    private final CaseService caseService;
    private final CaseAccessService caseAccess;
    private final PdfService pdfService;

    public CaseController(CaseService caseService, CaseAccessService caseAccess, PdfService pdfService) {
        this.caseService = caseService;
        this.caseAccess = caseAccess;
        this.pdfService = pdfService;
    }

    @Operation(
        summary = "Lista inskickade ärenden",
        description = """
            Hämtar inskickade ärenden med paginering. Utkast visas ej. Kräver handläggarbehörighet.

            `assignee`: `all` (standard), `mine` (tilldelade mig) eller `unassigned` (ej tilldelade).
            """
    )
    @ApiResponse(responseCode = "200", description = "Lista med ärenden")
    @GetMapping
    public Page<CaseDTO> getCases(
            @RequestParam(defaultValue = "all") String assignee,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UserInfo staff = CurrentUser.requireStaff();
        UUID assignedTo = "mine".equals(assignee) ? UUID.fromString(staff.id()) : null;
        boolean unassignedOnly = "unassigned".equals(assignee);
        Page<CaseDTO> page = caseService.getSubmittedCases(assignedTo, unassignedOnly, pageable)
                .map(CaseDTO::staffSummary);
        return withUnreadCounts(page, false);
    }

    @Operation(summary = "Handläggare som kan tilldelas ärenden")
    @GetMapping("/assignees")
    public List<AssigneeDTO> getAssignees() {
        CurrentUser.requireStaff();
        return caseService.getAssignableUsers().stream()
                .map(u -> new AssigneeDTO(u.getId(), u.getFullName()))
                .toList();
    }

    @Operation(
        summary = "Tilldela ärende",
        description = "Sätter ansvarig handläggare. `userId: null` tar bort tilldelningen."
    )
    @PutMapping("/{id}/assignee")
    public ResponseEntity<CaseDTO> assignCase(
            @PathVariable UUID id,
            @RequestBody AssignRequest request) {
        UserInfo staff = CurrentUser.requireStaff();
        Case updated = caseService.assign(id, request.userId(), UUID.fromString(staff.id()));
        return ResponseEntity.ok(CaseDTO.staffSummary(updated));
    }

    @Operation(
        summary = "Meddelanden i ärendet",
        description = "Meddelanden mellan medborgaren och handläggaren, äldst först. Interna anteckningar ingår inte."
    )
    @GetMapping("/{id}/messages")
    public List<CaseMessageDTO> getMessages(@PathVariable UUID id) {
        UserInfo user = caseAccess.requireRead(id);
        boolean forCitizen = !CurrentUser.isStaff(user);
        return caseService.getExternalMessages(id).stream()
                .map(m -> CaseMessageDTO.from(m, forCitizen))
                .toList();
    }

    @Operation(summary = "Skicka meddelande till handläggaren", description = "Medborgarens svar i sitt eget ärende.")
    @PostMapping("/{id}/messages")
    public ResponseEntity<CaseMessageDTO> sendCitizenMessage(
            @PathVariable UUID id,
            @RequestBody MessageRequest request) {
        UserInfo owner = caseAccess.requireOwner(id);
        ExternalMessage msg = caseService.addCitizenMessage(id, UUID.fromString(owner.id()), requireText(request));
        return ResponseEntity.ok(CaseMessageDTO.from(msg, true));
    }

    @Operation(summary = "Markera meddelanden som lästa", description = "Markerar motpartens meddelanden som lästa.")
    @PostMapping("/{id}/messages/read")
    public ResponseEntity<Void> markMessagesRead(@PathVariable UUID id) {
        UserInfo user = caseAccess.requireRead(id);
        caseService.markMessagesRead(id, UUID.fromString(user.id()), CurrentUser.isStaff(user));
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Hämta ett specifikt ärende",
        description = "Hämtar fullständig information om ett ärende inklusive alla svar."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ärendet hittades"),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CaseDTO> getCase(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id) {
        caseAccess.requireRead(id);
        return caseService.getCase(id)
                .map(CaseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Hämta ärende för handläggare",
        description = """
            Hämtar ett ärende med extra information för handläggare:
            - Händelsehistorik (events)
            - Interna meddelanden
            - Externa meddelanden
            - Statusdefinitioner för flödet
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ärendet med handläggardata"),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @GetMapping("/{id}/manager")
    public ResponseEntity<ManagerCaseDTO> getManagerCase(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id) {
        CurrentUser.requireStaff();
        return caseService.getCaseForManager(id)
                .map(c -> ManagerCaseDTO.from(c, caseService.allowedTransitions(c)))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Hämta ärende via referensnummer",
        description = "Hämtar ett ärende baserat på dess referensnummer (t.ex. 'EP-2024-000001')."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ärendet hittades"),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @GetMapping("/ref/{referenceNumber}")
    public ResponseEntity<CaseDTO> getCaseByReference(
            @Parameter(description = "Referensnummer", example = "EP-2024-000001", required = true)
            @PathVariable String referenceNumber) {
        UserInfo user = CurrentUser.require();
        return caseService.getCaseByReferenceNumber(referenceNumber)
                .filter(c -> caseAccess.canRead(c.getId(), user))
                .map(CaseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Hämta användarens ärenden",
        description = "Hämtar alla ärenden som tillhör en användare. Medborgare kan bara hämta sina egna."
    )
    @ApiResponse(responseCode = "200", description = "Användarens ärenden")
    @GetMapping("/user/{userId}")
    public Page<CaseDTO> getCasesForUser(
            @Parameter(description = "Användarens UUID", required = true)
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        requireSelfOrStaff(userId);
        return withUnreadCounts(caseService.getCasesForUser(userId, pageable).map(CaseDTO::summary), true);
    }

    @Operation(
        summary = "Hämta användarens utkast",
        description = "Hämtar alla ej inskickade ärenden (utkast) för en användare."
    )
    @ApiResponse(responseCode = "200", description = "Lista med utkast")
    @GetMapping("/user/{userId}/drafts")
    public List<CaseDTO> getDraftsForUser(
            @Parameter(description = "Användarens UUID", required = true)
            @PathVariable UUID userId) {
        requireSelfOrStaff(userId);
        return caseService.getDraftsForUser(userId).stream()
                .map(CaseDTO::summary)
                .toList();
    }

    @Operation(
        summary = "Skapa nytt ärende",
        description = """
            Skapar ett nytt ärende (utkast) baserat på en e-tjänst.

            Ärendet får automatiskt:
            - Ett unikt referensnummer
            - Status 'DRAFT'
            - Koppling till angiven e-tjänst
            - Den inloggade användaren som ägare
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Ärendet skapades"),
        @ApiResponse(responseCode = "400", description = "Ogiltig begäran")
    })
    @PostMapping
    public ResponseEntity<CaseDTO> createCase(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Data för nytt ärende",
                content = @Content(
                    schema = @Schema(implementation = CreateCaseRequest.class),
                    examples = @ExampleObject(value = """
                        {
                          "flowId": "00000000-0000-0000-0004-000000000001"
                        }
                        """)
                )
            )
            @RequestBody CreateCaseRequest request) {
        Case newCase = caseService.createCase(request.flowId(), CurrentUser.requireId());
        CaseDTO dto = CaseDTO.from(newCase);
        return ResponseEntity
                .created(URI.create("/api/v1/cases/" + newCase.getId()))
                .body(dto);
    }

    @Operation(
        summary = "Uppdatera ärendesvar",
        description = """
            Uppdaterar svaren i ett ärende.

            Skicka en map med query-ID som nyckel och svaret som värde:
            ```json
            {
              "query-uuid-1": "Svar på fråga 1",
              "query-uuid-2": true,
              "query-uuid-3": ["val1", "val2"]
            }
            ```
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Svaren uppdaterades"),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @PutMapping("/{id}/values")
    public ResponseEntity<CaseDTO> updateCaseValues(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id,
            @RequestBody Map<UUID, Object> values) {
        caseAccess.requireOwner(id);
        Case updated = caseService.updateCaseValues(id, values);
        return ResponseEntity.ok(CaseDTO.from(updated));
    }

    @Operation(
        summary = "Skicka in ärende",
        description = """
            Skickar in ett ärende för handläggning.

            Ärendet ändrar status från DRAFT till SUBMITTED och
            kan inte längre redigeras av medborgaren.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ärendet skickades in"),
        @ApiResponse(responseCode = "400", description = "Ärendet är redan inskickat"),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @PostMapping("/{id}/submit")
    public ResponseEntity<CaseDTO> submitCase(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id) {
        caseAccess.requireOwner(id);
        Case submitted = caseService.submitCase(id);
        return ResponseEntity.ok(CaseDTO.from(submitted));
    }

    @Operation(
        summary = "Ändra ärendestatus",
        description = """
            Ändrar status på ett ärende (handläggarfunktion).

            Möjliga statusar beror på e-tjänstens konfiguration.
            En kommentar kan läggas till för att förklara statusändringen.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status ändrades"),
        @ApiResponse(responseCode = "404", description = "Ärendet eller statusen hittades inte")
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<CaseDTO> changeStatus(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id,
            @RequestBody ChangeStatusRequest request) {
        UserInfo staff = CurrentUser.requireStaff();
        Case updated = caseService.changeStatus(id, request.statusId(), UUID.fromString(staff.id()), request.comment());
        return ResponseEntity.ok(CaseDTO.from(updated));
    }

    @Operation(
        summary = "Sök ärenden",
        description = "Fritextsökning bland ärenden. Söker i referensnummer och beskrivning. Kräver handläggarbehörighet."
    )
    @ApiResponse(responseCode = "200", description = "Sökresultat")
    @GetMapping("/search")
    public Page<CaseDTO> searchCases(
            @Parameter(description = "Sökfras", required = true, example = "EP-2024")
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        CurrentUser.requireStaff();
        return withUnreadCounts(caseService.searchCases(q, pageable).map(CaseDTO::staffSummary), false);
    }

    @Operation(
        summary = "Ta bort ärende",
        description = "Tar bort ett ärende (endast utkast kan tas bort)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Ärendet togs bort"),
        @ApiResponse(responseCode = "400", description = "Kan inte ta bort inskickat ärende"),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCase(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id) {
        caseAccess.requireOwner(id);
        caseService.deleteCase(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Lägg till internt meddelande",
        description = "Lägger till ett internt meddelande som endast är synligt för handläggare."
    )
    @ApiResponse(responseCode = "200", description = "Meddelandet lades till")
    @PostMapping("/{id}/messages/internal")
    public ResponseEntity<ManagerCaseDTO.InternalMessageDTO> addInternalMessage(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id,
            @RequestBody MessageRequest request) {
        UserInfo staff = CurrentUser.requireStaff();
        InternalMessage msg = caseService.addInternalMessage(id, UUID.fromString(staff.id()), requireText(request));
        return ResponseEntity.ok(ManagerCaseDTO.InternalMessageDTO.from(msg));
    }

    @Operation(
        summary = "Lägg till externt meddelande",
        description = "Lägger till ett meddelande som är synligt för medborgaren."
    )
    @ApiResponse(responseCode = "200", description = "Meddelandet lades till")
    @PostMapping("/{id}/messages/external")
    public ResponseEntity<ManagerCaseDTO.ExternalMessageDTO> addExternalMessage(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id,
            @RequestBody MessageRequest request) {
        UserInfo staff = CurrentUser.requireStaff();
        ExternalMessage msg = caseService.addExternalMessage(id, UUID.fromString(staff.id()), requireText(request), true);
        return ResponseEntity.ok(ManagerCaseDTO.ExternalMessageDTO.from(msg));
    }

    @Operation(
        summary = "Generera PDF",
        description = "Genererar ett PDF-dokument med ärendets alla uppgifter. Kräver handläggarbehörighet."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "PDF-dokument",
            content = @Content(mediaType = "application/pdf")
        ),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> generatePdf(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id) {
        CurrentUser.requireStaff();
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

    @Operation(
        summary = "Hämta händelsehistorik för ärende",
        description = """
            Returnerar kronologisk lista över ärendets händelser (CREATED, SUBMITTED,
            STATUS_CHANGED, MESSAGE_SENT, m.fl.).

            Medborgare ser endast egna ärenden; handläggare/admin ser alla. För medborgare
            maskas handläggarnamn till "Handläggare" (dataminimering).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Händelsehistorik"),
        @ApiResponse(responseCode = "403", description = "Saknar behörighet"),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @GetMapping("/{id}/events")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CaseEventDTO>> getCaseEvents(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id) {
        UserInfo user = caseAccess.requireRead(id);
        boolean maskStaffNames = !CurrentUser.isStaff(user);
        return caseService.getCaseForManager(id)
                .map(caseEntity -> {
                    List<CaseEventDTO> events = caseEntity.getEvents().stream()
                            .sorted(Comparator.comparing(
                                    e -> e.getCreatedAt() != null ? e.getCreatedAt() : java.time.Instant.EPOCH))
                            .filter(e -> !maskStaffNames || !e.isInternal())
                            .map(e -> maskStaffNames ? CaseEventDTO.forCitizen(e) : CaseEventDTO.forManager(e))
                            .toList();
                    return ResponseEntity.ok(events);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Ladda ned eget ärende som PDF",
        description = """
            Medborgarens egen nedladdning av sitt ärende som PDF.

            Till skillnad från `GET /{id}/pdf` (handläggarvyn) kontrolleras här att
            den inloggade användaren är ärendets ägare.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF-dokument",
                content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "403", description = "Saknar behörighet"),
        @ApiResponse(responseCode = "404", description = "Ärendet hittades inte")
    })
    @GetMapping("/{id}/pdf/own")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> generateOwnPdf(
            @Parameter(description = "Ärendets UUID", required = true)
            @PathVariable UUID id) {
        caseAccess.requireRead(id);
        return caseService.getCaseForManager(id)
                .map(caseEntity -> {
                    try {
                        byte[] pdfBytes = pdfService.generateCasePdf(caseEntity, caseEntity.getFlow());
                        String filename = "arende-" + caseEntity.getReferenceNumber() + ".pdf";

                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + filename + "\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .contentLength(pdfBytes.length)
                                .body(pdfBytes);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to generate PDF", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static final int MAX_MESSAGE_LENGTH = 5000;

    private static String requireText(MessageRequest request) {
        String text = request == null || request.message() == null ? "" : request.message().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meddelandet är tomt");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meddelandet är för långt");
        }
        return text;
    }

    /**
     * Add unread message counts to a page of cases. fromStaff=true counts the
     * staff's messages the citizen hasn't read, false the citizen's messages.
     */
    private Page<CaseDTO> withUnreadCounts(Page<CaseDTO> page, boolean fromStaff) {
        Map<UUID, Long> counts = caseService.unreadMessageCounts(
                page.getContent().stream().map(CaseDTO::id).toList(), fromStaff);
        return page.map(dto -> dto.withUnreadMessages(counts.getOrDefault(dto.id(), 0L)));
    }

    private void requireSelfOrStaff(UUID userId) {
        UserInfo user = CurrentUser.require();
        if (!CurrentUser.isStaff(user) && !user.id().equals(userId.toString())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Du kan bara se dina egna ärenden");
        }
    }

    // Request records with Schema annotations

    public record AssignRequest(
        @Schema(description = "Handläggarens UUID, eller null för att ta bort tilldelningen")
        UUID userId
    ) {}

    public record AssigneeDTO(UUID id, String name) {}

    @Schema(description = "Begäran för att skapa nytt ärende")
    public record CreateCaseRequest(
        @Schema(description = "E-tjänstens UUID", example = "00000000-0000-0000-0004-000000000001")
        UUID flowId
    ) {}

    @Schema(description = "Begäran för att ändra ärendestatus")
    public record ChangeStatusRequest(
        @Schema(description = "Ny status UUID")
        UUID statusId,
        @Schema(description = "Kommentar till statusändringen", example = "Ärendet behöver kompletterande uppgifter")
        String comment
    ) {}

    @Schema(description = "Begäran för att skicka meddelande")
    public record MessageRequest(
        @Schema(description = "Meddelandetext", example = "Vi behöver kompletterande handlingar")
        String message
    ) {}
}
