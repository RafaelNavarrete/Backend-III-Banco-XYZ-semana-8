package cl.duoc.pagos_service.model;

import java.math.BigDecimal;

public record Pago(Long id, String tipo, Long cuentaOrigen, Long cuentaDestino,
                   BigDecimal monto, String estado, String fecha) {
}