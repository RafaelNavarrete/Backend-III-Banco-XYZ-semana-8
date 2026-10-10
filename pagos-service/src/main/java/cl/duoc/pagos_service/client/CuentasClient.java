package cl.duoc.pagos_service.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
public class CuentasClient {

    private final RestClient restClient = RestClient.create();
    private final LoadBalancerClient loadBalancer;

    public CuentasClient(LoadBalancerClient loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    @Retry(name = "cuentasService", fallbackMethod = "fallbackCuenta")
    @CircuitBreaker(name = "cuentasService")
    @Bulkhead(name = "cuentasService")
    public ResultadoCuenta validarCuenta(Long cuentaId) {

        // Spring Cloud LoadBalancer elige una instancia registrada en Eureka (round-robin)
        ServiceInstance instancia = loadBalancer.choose("cuentas-service");
        if (instancia == null) {
            throw new IllegalStateException("No hay instancias de cuentas-service disponibles");
        }

        String token = tokenActual();
        try {
            restClient.get()
                    .uri(instancia.getUri() + "/api/cuentas/{id}", cuentaId)
                    .headers(h -> {
                        if (token != null) {
                            h.setBearerAuth(token);
                        }
                    })
                    .retrieve()
                    .toBodilessEntity();
            return ResultadoCuenta.EXISTE;
        } catch (HttpClientErrorException.NotFound e) {
            // 404 es una respuesta valida (la cuenta no existe), no una falla del servicio
            return ResultadoCuenta.NO_EXISTE;
        }
    }

    // Comportamiento alternativo: si cuentas-service falla, no se cae el pago
    public ResultadoCuenta fallbackCuenta(Long cuentaId, Throwable t) {
        return ResultadoCuenta.NO_DISPONIBLE;
    }

    private String tokenActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        return null;
    }
}