package cl.duoc.bank_batch.controller;

import cl.duoc.bank_batch.kafka.TransaccionProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kafka")
public class KafkaTestController {

    private final TransaccionProducer producer;

    public KafkaTestController(TransaccionProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/enviar")
    public ResponseEntity<String> enviar(
            @RequestParam String mensaje) {

        producer.enviarMensaje(mensaje);

        return ResponseEntity.ok(
                "Evento enviado a Kafka: " + mensaje
        );
    }
}