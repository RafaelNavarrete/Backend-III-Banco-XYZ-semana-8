package cl.duoc.bank_batch.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TransaccionProducer {

    private static final String TOPIC = "banco.transacciones";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public TransaccionProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void enviarMensaje(String mensaje) {
        kafkaTemplate.send(TOPIC, mensaje);

        System.out.println(
                "Evento enviado a Kafka [" + TOPIC + "]: " + mensaje
        );
    }
}