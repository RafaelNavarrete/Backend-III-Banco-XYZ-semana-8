package cl.duoc.bank_batch.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ClientesClient {

    private final RestClient restClient = RestClient.create();

    @Value("${clientes.url:http://localhost:8091}")
    private String clientesUrl;

    @Retry(name = "clientesService", fallbackMethod = "fallbackClientes")
    @CircuitBreaker(name = "clientesService")
    @Bulkhead(name = "clientesService")
    public String obtenerClientes() {
        String token = tokenActual();
        return restClient.get()
                .uri(clientesUrl + "/api/clientes")
                .headers(h -> {
                    if (token != null) {
                        h.setBearerAuth(token);
                    }
                })
                .retrieve()
                .body(String.class);
    }

    public String fallbackClientes(Throwable t) {
        return "{\"error\":\"clientes-service no disponible temporalmente\"}";
    }

    // Reenvia el mismo token OAuth2 que trajo la peticion (token relay)
    private String tokenActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        return null;
    }
}