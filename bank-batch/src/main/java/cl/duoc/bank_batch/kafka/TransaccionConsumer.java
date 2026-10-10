package cl.duoc.bank_batch.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TransaccionConsumer {

    @KafkaListener(
            topics = "banco.transacciones",
            groupId = "bank-batch-group"
    )
    public void recibirMensaje(String mensaje) {
        System.out.println("Evento recibido desde Kafka: " + mensaje);
    }

    @KafkaListener(
            topics = "alertas.seguridad",
            groupId = "bank-batch-group"
    )
    public void recibirAlerta(String mensaje) {
        System.out.println("ALERTA DE SEGURIDAD recibida: " + mensaje);
    }
}