package cl.duoc.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RutasConfig {

    // lb:// = balanceo de carga del lado del cliente usando los nombres registrados en Eureka
    @Bean
    public RouteLocator rutas(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("clientes", r -> r.path("/api/clientes/**").uri("lb://clientes-service"))
                .route("cuentas", r -> r.path("/api/cuentas/**").uri("lb://cuentas-service"))
                .route("pagos", r -> r.path("/api/pagos/**").uri("lb://pagos-service"))
                .build();
    }
}