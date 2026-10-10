package cl.duoc.clientes_service.repository;

import cl.duoc.clientes_service.model.Cliente;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class ClienteRepository {

    private final Map<Long, Cliente> clientes = new ConcurrentSkipListMap<>();
    private final AtomicLong secuencia = new AtomicLong(105);

    public ClienteRepository() {
        // Datos de ejemplo migrados desde bank_legacy_data
        agregar(new Cliente(101L, "Camila Rojas", "12.345.678-9", "camila.rojas@mail.cl"));
        agregar(new Cliente(102L, "Matías Fuentes", "13.456.789-0", "matias.fuentes@mail.cl"));
        agregar(new Cliente(103L, "Valentina Soto", "14.567.890-1", "valentina.soto@mail.cl"));
        agregar(new Cliente(104L, "Diego Herrera", "15.678.901-2", "diego.herrera@mail.cl"));
        agregar(new Cliente(105L, "Antonia Vega", "16.789.012-3", "antonia.vega@mail.cl"));
    }

    private void agregar(Cliente c) {
        clientes.put(c.getId(), c);
    }

    public List<Cliente> findAll() {
        return new ArrayList<>(clientes.values());
    }

    public Optional<Cliente> findById(Long id) {
        return Optional.ofNullable(clientes.get(id));
    }

    public boolean existeRut(String rut) {
        return clientes.values().stream().anyMatch(c -> c.getRut().equals(rut));
    }

    public Cliente crear(String nombre, String rut, String email) {
        Cliente c = new Cliente(secuencia.incrementAndGet(), nombre, rut, email);
        agregar(c);
        return c;
    }
}