package cl.duoc.cuentas_service.model;

import java.math.BigDecimal;

public class Cuenta {
    private Long id;
    private Long clienteId;
    private String tipo;
    private BigDecimal saldo;
    private String estado;

    public Cuenta(Long id, Long clienteId, String tipo, BigDecimal saldo, String estado) {
        this.id = id;
        this.clienteId = clienteId;
        this.tipo = tipo;
        this.saldo = saldo;
        this.estado = estado;
    }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getTipo() { return tipo; }
    public BigDecimal getSaldo() { return saldo; }
    public String getEstado() { return estado; }

    public void setTipo(String tipo) { this.tipo = tipo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }
    public void setEstado(String estado) { this.estado = estado; }
}