package se.eplatform.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import se.eplatform.cases.domain.Case;
import se.eplatform.cases.domain.QueryInstance;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.QueryDefinition;
import se.eplatform.flow.domain.Step;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] generateCasePdf(Case caseEntity, Flow flow) throws IOException {
        String html = buildCaseHtml(caseEntity, flow);
        return renderHtmlToPdf(html);
    }

    private byte[] renderHtmlToPdf(String html) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    private String buildCaseHtml(Case caseEntity, Flow flow) {
        // Create a map of query instances by definition ID for easy lookup
        Map<UUID, QueryInstance> instanceMap = caseEntity.getQueryInstances().stream()
                .collect(Collectors.toMap(
                        qi -> qi.getQueryDefinition().getId(),
                        qi -> qi,
                        (a, b) -> a
                ));

        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8"/>
                <style>
                    @page {
                        size: A4;
                        margin: 2cm;
                    }
                    body {
                        font-family: Arial, Helvetica, sans-serif;
                        font-size: 11pt;
                        line-height: 1.4;
                        color: #333;
                    }
                    .header {
                        border-bottom: 2px solid #2563eb;
                        padding-bottom: 15px;
                        margin-bottom: 20px;
                    }
                    .header h1 {
                        color: #1e40af;
                        margin: 0 0 5px 0;
                        font-size: 18pt;
                    }
                    .header .reference {
                        color: #666;
                        font-size: 10pt;
                    }
                    .meta-info {
                        background-color: #f3f4f6;
                        padding: 10px 15px;
                        margin-bottom: 20px;
                        border-radius: 4px;
                    }
                    .meta-info table {
                        width: 100%;
                        border-collapse: collapse;
                    }
                    .meta-info td {
                        padding: 3px 10px 3px 0;
                        font-size: 10pt;
                    }
                    .meta-info .label {
                        color: #666;
                        width: 120px;
                    }
                    .step {
                        margin-bottom: 25px;
                        page-break-inside: avoid;
                    }
                    .step-header {
                        background-color: #2563eb;
                        color: white;
                        padding: 8px 12px;
                        font-weight: bold;
                        font-size: 12pt;
                        margin-bottom: 0;
                    }
                    .step-content {
                        border: 1px solid #e5e7eb;
                        border-top: none;
                        padding: 15px;
                    }
                    .field {
                        margin-bottom: 12px;
                    }
                    .field-label {
                        font-weight: bold;
                        color: #374151;
                        font-size: 10pt;
                        margin-bottom: 3px;
                    }
                    .field-value {
                        padding: 5px 0;
                        min-height: 1em;
                    }
                    .field-value.empty {
                        color: #9ca3af;
                        font-style: italic;
                    }
                    .footer {
                        margin-top: 30px;
                        padding-top: 15px;
                        border-top: 1px solid #e5e7eb;
                        font-size: 9pt;
                        color: #666;
                    }
                    .status-badge {
                        display: inline-block;
                        padding: 3px 10px;
                        border-radius: 12px;
                        font-size: 10pt;
                        font-weight: bold;
                    }
                    .status-submitted { background-color: #fef3c7; color: #92400e; }
                    .status-in-progress { background-color: #dbeafe; color: #1e40af; }
                    .status-completed { background-color: #d1fae5; color: #065f46; }
                </style>
            </head>
            <body>
            """);

        // Header
        html.append("<div class=\"header\">");
        html.append("<h1>").append(escapeHtml(flow.getName())).append("</h1>");
        html.append("<div class=\"reference\">Reference: ").append(escapeHtml(caseEntity.getReferenceNumber())).append("</div>");
        html.append("</div>");

        // Meta information
        html.append("<div class=\"meta-info\">");
        html.append("<table>");
        html.append("<tr><td class=\"label\">Status:</td><td>");
        if (caseEntity.getStatus() != null) {
            String statusClass = getStatusClass(caseEntity.getStatus().getStatusType().name());
            html.append("<span class=\"status-badge ").append(statusClass).append("\">");
            html.append(escapeHtml(caseEntity.getStatus().getName()));
            html.append("</span>");
        }
        html.append("</td></tr>");
        html.append("<tr><td class=\"label\">Created:</td><td>")
                .append(formatInstant(caseEntity.getCreatedAt()))
                .append("</td></tr>");
        if (caseEntity.getSubmittedAt() != null) {
            html.append("<tr><td class=\"label\">Submitted:</td><td>")
                    .append(formatInstant(caseEntity.getSubmittedAt()))
                    .append("</td></tr>");
        }
        html.append("</table>");
        html.append("</div>");

        // Steps and fields
        List<Step> steps = flow.getSteps().stream()
                .sorted((a, b) -> a.getSortOrder() - b.getSortOrder())
                .toList();

        for (Step step : steps) {
            html.append("<div class=\"step\">");
            html.append("<div class=\"step-header\">").append(escapeHtml(step.getName())).append("</div>");
            html.append("<div class=\"step-content\">");

            List<QueryDefinition> queries = step.getQueryDefinitions().stream()
                    .sorted((a, b) -> a.getSortOrder() - b.getSortOrder())
                    .toList();

            for (QueryDefinition query : queries) {
                // Skip hidden fields in PDF
                if (query.getQueryType().name().equals("HIDDEN")) {
                    continue;
                }

                QueryInstance instance = instanceMap.get(query.getId());
                String value = formatValue(instance, query);

                html.append("<div class=\"field\">");
                html.append("<div class=\"field-label\">").append(escapeHtml(query.getName())).append("</div>");
                if (value != null && !value.isBlank()) {
                    html.append("<div class=\"field-value\">").append(escapeHtml(value)).append("</div>");
                } else {
                    html.append("<div class=\"field-value empty\">-</div>");
                }
                html.append("</div>");
            }

            html.append("</div>");
            html.append("</div>");
        }

        // Footer
        html.append("<div class=\"footer\">");
        html.append("Document generated: ").append(java.time.LocalDateTime.now().format(DATE_FORMAT));
        html.append(" | e-Plattform");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    private String formatValue(QueryInstance instance, QueryDefinition definition) {
        if (instance == null || instance.getValue() == null) {
            return null;
        }

        Object value = instance.getValue();

        // Handle different value types
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }

        if (value instanceof Map<?, ?> map) {
            // For complex objects, format nicely
            return map.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));
        }

        return value.toString();
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return "-";
        return instant.atZone(ZoneId.of("Europe/Stockholm")).format(DATE_FORMAT);
    }

    private String getStatusClass(String statusType) {
        return switch (statusType) {
            case "INITIAL" -> "status-submitted";
            case "IN_PROGRESS", "WAITING" -> "status-in-progress";
            case "COMPLETED" -> "status-completed";
            default -> "";
        };
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
