package se.eplatform.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:e-Plattform API}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("e-Plattform API")
                        .version("1.0.0")
                        .description("""
                                ## e-Plattform REST API

                                Modern e-tjänstplattform för offentlig förvaltning.

                                ### Autentisering

                                API:et använder JWT Bearer tokens för autentisering.
                                Skicka token i `Authorization` header:

                                ```
                                Authorization: Bearer <your-token>
                                ```

                                För att få en token, använd `/api/v1/public/auth/login` endpoint.

                                ### Testanvändare (utvecklingsmiljö)

                                | Email | Roll | Behörigheter |
                                |-------|------|--------------|
                                | admin@example.com | Admin | Alla behörigheter |
                                | handlaggare@example.com | Handläggare | Ärendehantering |
                                | medborgare@example.com | Medborgare | Skapa ärenden |

                                ### Rate Limiting

                                API:et har rate limiting:
                                - Generellt: 100 requests/minut
                                - Auth endpoints: 10 requests/minut
                                - Filuppladdning: 20 requests/minut

                                ### Felhantering

                                Alla fel returneras i format:
                                ```json
                                {
                                  "timestamp": "2024-01-01T12:00:00Z",
                                  "status": 400,
                                  "error": "Bad Request",
                                  "message": "Beskrivning av felet",
                                  "path": "/api/v1/..."
                                }
                                ```
                                """)
                        .contact(new Contact()
                                .name("e-Plattform Team")
                                .email("support@eplatform.se")
                                .url("https://github.com/Cetriq/e-plattform"))
                        .license(new License()
                                .name("AGPL-3.0")
                                .url("https://www.gnu.org/licenses/agpl-3.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Lokal utvecklingsmiljö"),
                        new Server()
                                .url("https://api.eplatform.se")
                                .description("Produktionsmiljö")))
                .tags(List.of(
                        new Tag().name("Auth").description("Autentisering och användarhantering"),
                        new Tag().name("Flows").description("E-tjänster och formulär"),
                        new Tag().name("Cases").description("Ärenden och ansökningar"),
                        new Tag().name("Files").description("Filhantering och uppladdning"),
                        new Tag().name("Categories").description("Kategorier för e-tjänster"),
                        new Tag().name("Flow Types").description("Typer av e-tjänster"),
                        new Tag().name("Statistics").description("Statistik och rapporter (admin)"),
                        new Tag().name("Manager").description("Handläggarfunktioner")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token från /api/v1/public/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
