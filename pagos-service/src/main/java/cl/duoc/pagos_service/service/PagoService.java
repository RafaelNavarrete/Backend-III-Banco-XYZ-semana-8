package cl.duoc.pagos_service.service;

import cl.duoc.pagos_service.client.CuentasClient;
import cl.duoc.pagos_service.client.ResultadoCuenta;
import cl.duoc.pagos_service.model.Pago;
import cl.duoc.pagos_service.model.PagoRequest;
import cl.duoc.pagos_service.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PagoService {

    private final PagoRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final CuentasClient cuentasClient;

    @Value("${pagos.topic}")
    private String topic;

    @Value("${pagos.topic.alertas:alertas.seguridad}")
    private String topicAlertas;

    public PagoService(PagoRepository repository,
                       KafkaTemplate<String, String> kafka,
                       CuentasClient cuentasClient) {
        this.repository = repository;
        this.kafka = kafka;
        this.cuentasClient = cuentasClient;
    }

    public Pago registrar(String tipo, PagoRequest req) {
        validar(tipo, req);

        // Se valida cada cuenta contra cuentas-service (con Resilience4j)
        String estado = "COMPLETADO";
        for (Long cuentaId : cuentasInvolucradas(tipo, req)) {
            ResultadoCuenta resultado = cuentasClient.validarCuenta(cuentaId);
            if (resultado == ResultadoCuenta.NO_EXISTE) {
                throw error("La cuenta " + cuentaId + " no existe");
            }
            if (resultado == ResultadoCuenta.NO_DISPONIBLE) {
                // Fallback: se acepta el pago y queda pendiente de validacion
                estado = "PENDIENTE_VALIDACION";
            }
        }

        Pago pago = repository.guardar(tipo, req.cuentaOrigen(), req.cuentaDestino(), req.monto(), estado);

        String nombreEvento = "COMPLETADO".equals(estado) ? "PAGO_COMPLETADO" : "PAGO_PENDIENTE";
        String evento = String.format(
                "{\"evento\":\"%s\",\"id\":%d,\"tipo\":\"%s\",\"cuentaOrigen\":%s,\"cuentaDestino\":%s,\"monto\":%s}",
                nombreEvento, pago.id(), pago.tipo(), pago.cuentaOrigen(), pago.cuentaDestino(),
                pago.monto().toPlainString());
        kafka.send(topic, String.valueOf(pago.id()), evento);

        // Alerta de seguridad para montos altos
        if (pago.monto().compareTo(new BigDecimal("1000000")) >= 0) {
            String alerta = String.format(
                    "{\"evento\":\"ALERTA_SEGURIDAD\",\"motivo\":\"MONTO_ALTO\",\"pagoId\":%d,\"monto\":%s}",
                    pago.id(), pago.monto().toPlainString());
            kafka.send(topicAlertas, String.valueOf(pago.id()), alerta);
        }

        return pago;
    }

    private List<Long> cuentasInvolucradas(String tipo, PagoRequest req) {
        return switch (tipo) {
            case "PAGO" -> List.of(req.cuentaOrigen());
            case "TRANSFERENCIA" -> List.of(req.cuentaOrigen(), req.cuentaDestino());
            default -> List.of(req.cuentaDestino());   // DEPOSITO
        };
    }

    private void validar(String tipo, PagoRequest req) {
        BigDecimal monto = req.monto();
        if (monto == null || monto.signum() <= 0) {
            throw error("El monto debe ser mayor a cero");
        }
        switch (tipo) {
            case "PAGO" -> {
                if (req.cuentaOrigen() == null) throw error("Falta cuentaOrigen");
            }
            case "TRANSFERENCIA" -> {
                if (req.cuentaOrigen() == null || req.cuentaDestino() == null)
                    throw error("Faltan cuentaOrigen y cuentaDestino");
                if (req.cuentaOrigen().equals(req.cuentaDestino()))
                    throw error("Las cuentas deben ser distintas");
            }
            case "DEPOSITO" -> {
                if (req.cuentaDestino() == null) throw error("Falta cuentaDestino");
            }
            default -> throw error("Tipo no soportado");
        }
    }

    private ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}