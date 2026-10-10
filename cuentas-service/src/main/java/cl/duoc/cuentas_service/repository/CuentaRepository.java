package cl.duoc.cuentas_service.repository;

import cl.duoc.cuentas_service.model.Cuenta;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class CuentaRepository {

    private final Map<Long, Cuenta> cuentas = new ConcurrentSkipListMap<>();
    private final AtomicLong secuencia = new AtomicLong(205);

    public CuentaRepository() {
        agregar(new Cuenta(201L, 101L, "corriente", new BigDecimal("450000"), "ACTIVA"));
        agregar(new Cuenta(202L, 102L, "ahorro", new BigDecimal("1200000"), "ACTIVA"));
        agregar(new Cuenta(203L, 103L, "corriente", new BigDecimal("87000"), "ACTIVA"));
        agregar(new Cuenta(204L, 104L, "ahorro", new BigDecimal("3050000"), "ACTIVA"));
        agregar(new Cuenta(205L, 105L, "corriente", new BigDecimal("15000"), "ACTIVA"));
    }

    private void agregar(Cuenta c) {
        cuentas.put(c.getId(), c);
    }

    public List<Cuenta> findAll() {
        return new ArrayList<>(cuentas.values());
    }

    public Optional<Cuenta> findById(Long id) {
        return Optional.ofNullable(cuentas.get(id));
    }

    public Cuenta abrir(Long clienteId, String tipo, BigDecimal saldoInicial) {
        Cuenta c = new Cuenta(secuencia.incrementAndGet(), clienteId, tipo, saldoInicial, "ACTIVA");
        agregar(c);
        return c;
    }

    // ---- Movimientos de saldo (aplicados por eventos de Kafka) ----

    public synchronized boolean debitar(Long id, BigDecimal monto) {
        Cuenta c = cuentas.get(id);
        if (!puedeDebitar(c, monto)) {
            return false;
        }
        c.setSaldo(c.getSaldo().subtract(monto));
        return true;
    }

    public synchronized boolean acreditar(Long id, BigDecimal monto) {
        Cuenta c = cuentas.get(id);
        if (c == null || !"ACTIVA".equals(c.getEstado())) {
            return false;
        }
        c.setSaldo(c.getSaldo().add(monto));
        return true;
    }

    public synchronized boolean transferir(Long origen, Long destino, BigDecimal monto) {
        Cuenta o = cuentas.get(origen);
        Cuenta d = cuentas.get(destino);
        if (!puedeDebitar(o, monto) || d == null || !"ACTIVA".equals(d.getEstado())) {
            return false;
        }
        o.setSaldo(o.getSaldo().subtract(monto));
        d.setSaldo(d.getSaldo().add(monto));
        return true;
    }

    private boolean puedeDebitar(Cuenta c, BigDecimal monto) {
        return c != null
                && "ACTIVA".equals(c.getEstado())
                && c.getSaldo().compareTo(monto) >= 0;
    }
}