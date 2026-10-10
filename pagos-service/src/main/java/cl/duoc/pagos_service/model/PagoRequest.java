package cl.duoc.pagos_service.model;

import java.math.BigDecimal;

public record PagoRequest(Long cuentaOrigen, Long cuentaDestino, BigDecimal monto) {
}