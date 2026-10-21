package se.eplatform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Checks definition and application documents against their JSON Schemas
 * (resources/contracts/schema). The schemas are the published description of
 * the formats.
 */
@Component
public class ContractDocumentValidator {

    public static final String DEFINITION_FORMAT = "eplatform.definition/1";
    public static final String APPLICATION_FORMAT = "eplatform.application/1";

    private final Map<String, JsonSchema> schemas;

    public ContractDocumentValidator() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        this.schemas = Map.of(
                DEFINITION_FORMAT, load(factory, "/contracts/schema/definition-1.schema.json"),
                APPLICATION_FORMAT, load(factory, "/contracts/schema/application-1.schema.json"));
    }

    /** The problems with a document, as "path: message"; empty when it is valid. */
    public List<String> validate(String format, JsonNode document) {
        JsonSchema schema = schemas.get(format);
        if (schema == null) {
            return List.of("$: unknown format " + format);
        }
        return schema.validate(document).stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .toList();
    }

    private static JsonSchema load(JsonSchemaFactory factory, String path) {
        try (InputStream in = ContractDocumentValidator.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing schema " + path);
            return factory.getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
