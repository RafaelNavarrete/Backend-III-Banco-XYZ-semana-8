package cl.duoc.pagos_service.controller;

import cl.duoc.pagos_service.model.Pago;
import cl.duoc.pagos_service.model.PagoRequest;
import cl.duoc.pagos_service.repository.PagoRepository;
import cl.duoc.pagos_service.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService service;
    private final PagoRepository repository;

    public PagoController(PagoService service, PagoRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping
    public List<Pago> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Pago buscar(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    @PostMapping("/pago")
    public Pago pago(@RequestBody PagoRequest req) {
        return service.registrar("PAGO", req);
    }

    @PostMapping("/transferencia")
    public Pago transferencia(@RequestBody PagoRequest req) {
        return service.registrar("TRANSFERENCIA", req);
    }

    @PostMapping("/deposito")
    public Pago deposito(@RequestBody PagoRequest req) {
        return service.registrar("DEPOSITO", req);
    }
}