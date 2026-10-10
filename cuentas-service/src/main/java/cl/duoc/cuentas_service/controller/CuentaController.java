package cl.duoc.cuentas_service.controller;

import cl.duoc.cuentas_service.model.Cuenta;
import cl.duoc.cuentas_service.model.CuentaRequest;
import cl.duoc.cuentas_service.repository.CuentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private final CuentaRepository repository;

    public CuentaController(CuentaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Cuenta> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Cuenta buscar(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
    }

    // Apertura de cuenta
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Cuenta abrir(@RequestBody CuentaRequest req) {
        if (req.clienteId() == null) {
            throw error(HttpStatus.BAD_REQUEST, "Falta clienteId");
        }
        validarTipo(req.tipo());
        BigDecimal saldo = req.saldoInicial() == null ? BigDecimal.ZERO : req.saldoInicial();
        if (saldo.signum() < 0) {
            throw error(HttpStatus.BAD_REQUEST, "El saldo inicial no puede ser negativo");
        }
        return repository.abrir(req.clienteId(), req.tipo(), saldo);
    }

    // Mantenimiento: cambio de tipo de cuenta
    @PutMapping("/{id}")
    public Cuenta actualizar(@PathVariable Long id, @RequestBody CuentaRequest req) {
        Cuenta cuenta = buscar(id);
        validarTipo(req.tipo());
        cuenta.setTipo(req.tipo());
        return cuenta;
    }

    // Cierre de cuenta (solo si el saldo es cero)
    @DeleteMapping("/{id}")
    public Cuenta cerrar(@PathVariable Long id) {
        Cuenta cuenta = buscar(id);
        if (cuenta.getSaldo().signum() != 0) {
            throw error(HttpStatus.CONFLICT, "La cuenta debe tener saldo cero para cerrarse");
        }
        cuenta.setEstado("CERRADA");
        return cuenta;
    }

    private void validarTipo(String tipo) {
        if (!"corriente".equals(tipo) && !"ahorro".equals(tipo)) {
            throw error(HttpStatus.BAD_REQUEST, "El tipo debe ser corriente o ahorro");
        }
    }

    private ResponseStatusException error(HttpStatus status, String mensaje) {
        return new ResponseStatusException(status, mensaje);
    }
}