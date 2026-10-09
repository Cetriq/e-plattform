package se.eplatform.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The client's IP address. Only a header that our own edge proxy sets and
 * overwrites is trusted (x-vercel-forwarded-for on Vercel); clients can set
 * any other header. Otherwise, with server.forward-headers-strategy=native,
 * Tomcat resolves the address from trusted proxies only.
 */
@Component
public class ClientIp {

    private final String trustedHeader;

    public ClientIp(@Value("${eplatform.security.client-ip-header:}") String trustedHeader) {
        this.trustedHeader = trustedHeader;
    }

    public String of(HttpServletRequest request) {
        if (!trustedHeader.isBlank()) {
            String value = request.getHeader(trustedHeader);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
