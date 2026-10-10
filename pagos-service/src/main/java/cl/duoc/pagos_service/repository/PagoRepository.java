package cl.duoc.pagos_service.repository;

import cl.duoc.pagos_service.model.Pago;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class PagoRepository {

    private final List<Pago> pagos = new CopyOnWriteArrayList<>();
    private final AtomicLong secuencia = new AtomicLong(1000);

    public Pago guardar(String tipo, Long origen, Long destino, BigDecimal monto, String estado) {
        Pago pago = new Pago(secuencia.incrementAndGet(), tipo, origen, destino,
                monto, estado, LocalDateTime.now().toString());
        pagos.add(pago);
        return pago;
    }

    public List<Pago> findAll() {
        return pagos;
    }

    public Optional<Pago> findById(Long id) {
        return pagos.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}