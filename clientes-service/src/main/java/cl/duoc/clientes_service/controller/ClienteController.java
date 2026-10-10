package cl.duoc.clientes_service.controller;

import cl.duoc.clientes_service.model.Cliente;
import cl.duoc.clientes_service.model.ClienteRequest;
import cl.duoc.clientes_service.repository.ClienteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository repository;

    public ClienteController(ClienteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Cliente> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Cliente buscar(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Cliente crear(@RequestBody ClienteRequest req) {
        validar(req);
        if (repository.existeRut(req.rut())) {
            throw error(HttpStatus.CONFLICT, "Ya existe un cliente con ese RUT");
        }
        return repository.crear(req.nombre(), req.rut(), req.email());
    }

    @PutMapping("/{id}")
    public Cliente actualizar(@PathVariable Long id, @RequestBody ClienteRequest req) {
        Cliente cliente = buscar(id);
        if (req.nombre() == null || req.nombre().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "Falta el nombre");
        }
        cliente.setNombre(req.nombre());
        cliente.setEmail(req.email());
        return cliente;
    }

    private void validar(ClienteRequest req) {
        if (req.nombre() == null || req.nombre().isBlank()
                || req.rut() == null || req.rut().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "Nombre y RUT son obligatorios");
        }
    }

    private ResponseStatusException error(HttpStatus status, String mensaje) {
        return new ResponseStatusException(status, mensaje);
    }
}