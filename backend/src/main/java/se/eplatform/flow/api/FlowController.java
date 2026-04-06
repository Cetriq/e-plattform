package se.eplatform.flow.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.flow.api.dto.FlowDTO;
import se.eplatform.flow.service.FlowService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flows")
@Tag(name = "Flows", description = """
    E-tjänster och formulär.

    En **Flow** representerar en e-tjänst som medborgare kan använda för att skapa ärenden.
    Varje flow innehåller steg (steps) med frågor (queries) som användaren ska besvara.
    """)
public class FlowController {

    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    @Operation(
        summary = "Lista alla tillgängliga e-tjänster",
        description = """
            Hämtar alla publicerade och aktiverade e-tjänster.

            Returnerar endast e-tjänster som:
            - Har status PUBLISHED
            - Är aktiverade (enabled = true)
            - Har passerat eventuellt publiceringsdatum
            - Inte har passerat eventuellt avpubliceringsdatum
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Lista med e-tjänster",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = FlowDTO.class)
        )
    )
    @GetMapping
    public List<FlowDTO> getFlows() {
        return flowService.getAccessibleFlows().stream()
                .map(FlowDTO::summary)
                .toList();
    }

    @Operation(
        summary = "Lista e-tjänster med paginering",
        description = "Hämtar publicerade e-tjänster med stöd för paginering och sortering."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Paginerad lista med e-tjänster"
    )
    @GetMapping("/list")
    public Page<FlowDTO> getFlowsPaged(
            @Parameter(description = "Sidnummer (0-indexerat)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Antal per sida", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @PageableDefault(size = 20) Pageable pageable) {
        return flowService.getPublishedFlows(pageable)
                .map(FlowDTO::summary);
    }

    @Operation(
        summary = "Hämta en specifik e-tjänst",
        description = """
            Hämtar fullständig information om en e-tjänst inklusive:
            - Alla steg (steps)
            - Alla frågor (queries) per steg
            - Statusdefinitioner
            - Kategori och typ
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "E-tjänst hittad",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = FlowDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "E-tjänst hittades inte"
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<FlowDTO> getFlow(
            @Parameter(description = "E-tjänstens UUID", required = true)
            @PathVariable UUID id) {
        return flowService.getFlow(id)
                .map(FlowDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Hämta e-tjänster per typ",
        description = "Hämtar alla publicerade e-tjänster av en viss typ (t.ex. 'Byggande & Boende')."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Lista med e-tjänster av angiven typ"
    )
    @GetMapping("/by-type/{typeId}")
    public List<FlowDTO> getFlowsByType(
            @Parameter(description = "Typens UUID", required = true)
            @PathVariable UUID typeId) {
        return flowService.getFlowsByType(typeId).stream()
                .map(FlowDTO::summary)
                .toList();
    }

    @Operation(
        summary = "Hämta e-tjänster per kategori",
        description = "Hämtar alla publicerade e-tjänster inom en viss kategori."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Lista med e-tjänster i angiven kategori"
    )
    @GetMapping("/by-category/{categoryId}")
    public List<FlowDTO> getFlowsByCategory(
            @Parameter(description = "Kategorins UUID", required = true)
            @PathVariable UUID categoryId) {
        return flowService.getFlowsByCategory(categoryId).stream()
                .map(FlowDTO::summary)
                .toList();
    }

    @Operation(
        summary = "Sök e-tjänster",
        description = """
            Fritextsökning bland publicerade e-tjänster.

            Söker i:
            - Namn
            - Kort beskrivning
            - Taggar
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Sökresultat med paginering"
    )
    @GetMapping("/search")
    public Page<FlowDTO> searchFlows(
            @Parameter(description = "Sökfras", required = true, example = "bygglov")
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return flowService.searchFlows(q, pageable)
                .map(FlowDTO::summary);
    }

    @Operation(
        summary = "Hämta alla versioner av en e-tjänst",
        description = """
            Hämtar alla versioner av en e-tjänstfamilj.

            E-tjänster versionshanteras genom en "family" som grupperar
            alla versioner av samma e-tjänst.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Lista med alla versioner"
    )
    @GetMapping("/family/{familyId}/versions")
    public List<FlowDTO> getFlowVersions(
            @Parameter(description = "Familjens UUID", required = true)
            @PathVariable UUID familyId) {
        return flowService.getFlowVersions(familyId).stream()
                .map(FlowDTO::summary)
                .toList();
    }
}
