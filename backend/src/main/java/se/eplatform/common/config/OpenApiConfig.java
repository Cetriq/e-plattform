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

                                ### Logga in i demon

                                Anropa `POST /api/v1/public/auth/login` med e-post och demons
                                åtkomstkod, och klicka sedan på **Authorize** och klistra in
                                `token` från svaret.

                                ```json
                                { "email": "handlaggare@example.com", "accessCode": "<åtkomstkod>" }
                                ```

                                | E-post | Roll |
                                |--------|------|
                                | medborgare@example.com | Medborgare |
                                | handlaggare@example.com | Handläggare |
                                | admin@example.com | Administratör och e-tjänstredaktör |
                                | informationssakerhet@example.com | Informationssäkerhet & dataskydd |
                                | it-drift@example.com | IT & drift |

                                ### Begränsningar

                                Per IP-adress och minut: 100 anrop, 10 inloggningar och 20 filuppladdningar.

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
                        // Relative: requests go to the site the documentation is served from
                        new Server()
                                .url("/")
                                .description("Den här miljön")))
                .tags(List.of(
                        new Tag().name("Auth").description("Inloggning, profil och sessioner"),
                        new Tag().name("Flows").description("Publicerade e-tjänster och formulär"),
                        new Tag().name("Cases").description("Ärenden, meddelanden och handläggning"),
                        new Tag().name("Filer").description("Uppladdning och nedladdning av bilagor"),
                        new Tag().name("Admin - E-tjänster").description("Skapa och redigera e-tjänster"),
                        new Tag().name("Admin - Kategorier").description("Kategorier och tjänstetyper"),
                        new Tag().name("Admin - Statistik").description("Statistik över e-tjänster och ärenden"),
                        new Tag().name("Admin - Användare").description("Användare och roller"),
                        new Tag().name("Informationssäkerhet & dataskydd").description("Spårbarhetslogg, registerutdrag, radering och gallring"),
                        new Tag().name("IT & drift").description("Driftstatus och systemlogg")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token från /api/v1/public/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
