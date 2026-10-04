package cl.duoc.bank_batch.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
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
        return restClient.get()
                .uri(clientesUrl + "/api/clientes")
                .header("Authorization", "Basic YWRtaW46YWRtaW4xMjM=")
                .retrieve()
                .body(String.class);
    }

    public String fallbackClientes(Throwable t) {
        return "{\"error\":\"clientes-service no disponible temporalmente\"}";
    }
}