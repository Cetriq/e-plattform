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
@Tag(name = "Cases", description = """
    Ärenden och ansökningar.

    Ett **Case** (ärende) skapas när en medborgare påbörjar en ansökan via en e-tjänst (Flow).
    Ärendet innehåller alla svar (QueryInstances) och går genom olika statusar under handläggning.
    """)
public class CaseController {

    private final CaseService caseService;
    private final PdfService pdfService;

    public CaseController(CaseService caseService, PdfService pdfService) {
        this.caseService = caseService;
        this.pdfService = pdfService;
    }

    @Operation(
        summary = "Lista inskickade ärenden",
        description = "Hämtar alla inskickade ärenden med paginering. Utkast (ej inskickade) visas ej."
    )
    @ApiResponse(responseCode = "200", description = "Lista med ärenden")
    @GetMapping
    public Page<CaseDTO> getCases(@PageableDefault(size = 20) Pageable pageable) {
        return caseService.getSubmittedCases(pageable)
                .map(CaseDTO::summary);
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
        return caseService.getCaseForManager(id)
                .map(ManagerCaseDTO::from)
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
        return caseService.getCaseByReferenceNumber(referenceNumber)
                .map(CaseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Hämta användarens ärenden",
        description = "Hämtar alla ärenden som tillhör en specifik användare."
    )
    @ApiResponse(responseCode = "200", description = "Användarens ärenden")
    @GetMapping("/user/{userId}")
    public Page<CaseDTO> getCasesForUser(
            @Parameter(description = "Användarens UUID", required = true)
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return caseService.getCasesForUser(userId, pageable)
                .map(CaseDTO::summary);
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
                          "flowId": "00000000-0000-0000-0004-000000000001",
                          "userId": "00000000-0000-0000-0000-000000000102"
                        }
                        """)
                )
            )
            @RequestBody CreateCaseRequest request) {
        Case newCase = caseService.createCase(request.flowId(), request.userId());
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
        Case updated = caseService.changeStatus(id, request.statusId(), request.userId(), request.comment());
        return ResponseEntity.ok(CaseDTO.from(updated));
    }

    @Operation(
        summary = "Sök ärenden",
        description = "Fritextsökning bland ärenden. Söker i referensnummer och beskrivning."
    )
    @ApiResponse(responseCode = "200", description = "Sökresultat")
    @GetMapping("/search")
    public Page<CaseDTO> searchCases(
            @Parameter(description = "Sökfras", required = true, example = "EP-2024")
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return caseService.searchCases(q, pageable)
                .map(CaseDTO::summary);
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
        InternalMessage msg = caseService.addInternalMessage(id, request.userId(), request.message());
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
        ExternalMessage msg = caseService.addExternalMessage(id, request.userId(), request.message(), true);
        return ResponseEntity.ok(ManagerCaseDTO.ExternalMessageDTO.from(msg));
    }

    @Operation(
        summary = "Generera PDF",
        description = "Genererar ett PDF-dokument med ärendets alla uppgifter."
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

    // Request records with Schema annotations

    @Schema(description = "Begäran för att skapa nytt ärende")
    public record CreateCaseRequest(
        @Schema(description = "E-tjänstens UUID", example = "00000000-0000-0000-0004-000000000001")
        UUID flowId,
        @Schema(description = "Användarens UUID", example = "00000000-0000-0000-0000-000000000102")
        UUID userId
    ) {}

    @Schema(description = "Begäran för att ändra ärendestatus")
    public record ChangeStatusRequest(
        @Schema(description = "Ny status UUID")
        UUID statusId,
        @Schema(description = "Handläggarens UUID")
        UUID userId,
        @Schema(description = "Kommentar till statusändringen", example = "Ärendet behöver kompletterande uppgifter")
        String comment
    ) {}

    @Schema(description = "Begäran för att skicka meddelande")
    public record MessageRequest(
        @Schema(description = "Avsändarens UUID")
        UUID userId,
        @Schema(description = "Meddelandetext", example = "Vi behöver kompletterande handlingar")
        String message
    ) {}
}
