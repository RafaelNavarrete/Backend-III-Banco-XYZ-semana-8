package cl.duoc.cuentas_service.kafka;

import cl.duoc.cuentas_service.repository.CuentaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PagoEventosConsumer {

    private static final Logger log = LoggerFactory.getLogger(PagoEventosConsumer.class);

    private final CuentaRepository repository;

    public PagoEventosConsumer(CuentaRepository repository) {
        this.repository = repository;
    }

    // Cada instancia usa su propio grupo para aplicar TODOS los eventos a su copia en memoria
    @KafkaListener(topics = "${cuentas.topic:pagos.completados}", groupId = "cuentas-${random.uuid}")
    public void recibir(String mensaje) {

        String evento = valor(mensaje, "evento");
        if (!"PAGO_COMPLETADO".equals(evento)) {
            log.info("Evento ignorado ({}): {}", evento, mensaje);
            return;
        }

        String tipo = valor(mensaje, "tipo");
        BigDecimal monto = new BigDecimal(valor(mensaje, "monto"));
        Long origen = aId(valor(mensaje, "cuentaOrigen"));
        Long destino = aId(valor(mensaje, "cuentaDestino"));

        boolean aplicado = switch (tipo) {
            case "PAGO" -> repository.debitar(origen, monto);
            case "TRANSFERENCIA" -> repository.transferir(origen, destino, monto);
            case "DEPOSITO" -> repository.acreditar(destino, monto);
            default -> false;
        };

        log.info("Evento {} {} sobre saldos: {}", tipo, mensaje, aplicado ? "APLICADO" : "NO APLICADO (saldo insuficiente o cuenta invalida)");
    }

    private String valor(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\":(\"[^\"]*\"|[^,}]*)").matcher(json);
        return m.find() ? m.group(1).replace("\"", "").trim() : null;
    }

    private Long aId(String texto) {
        return (texto == null || texto.equals("null")) ? null : Long.valueOf(texto);
    }
}