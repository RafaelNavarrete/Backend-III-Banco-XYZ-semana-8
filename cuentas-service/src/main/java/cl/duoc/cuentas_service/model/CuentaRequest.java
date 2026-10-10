package cl.duoc.cuentas_service.model;

import java.math.BigDecimal;

public record CuentaRequest(Long clienteId, String tipo, BigDecimal saldoInicial) {
}