package cl.duoc.bank_batch.bff.cajero;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class CajeroService {

    private final JdbcTemplate jdbcTemplate;

    public CajeroService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RetiroResponseDTO realizarRetiro(Long cuentaId, BigDecimal monto) {

        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a cero");
        }

        BigDecimal saldoActual;
        try {
            // FOR UPDATE bloquea la fila: dos retiros simultaneos no pueden leer el mismo saldo
            saldoActual = jdbcTemplate.queryForObject(
                    """
                    SELECT saldo_final
                    FROM cuentas_intereses
                    WHERE cuenta_id = ?
                    FOR UPDATE
                    """,
                    BigDecimal.class,
                    cuentaId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La cuenta " + cuentaId + " no existe");
        }

        if (saldoActual == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La cuenta " + cuentaId + " no existe");
        }

        if (monto.compareTo(saldoActual) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente");
        }

        BigDecimal saldoPosterior = saldoActual.subtract(monto);

        jdbcTemplate.update(
                """
                UPDATE cuentas_intereses
                SET saldo_final = ?
                WHERE cuenta_id = ?
                """,
                saldoPosterior,
                cuentaId
        );

        jdbcTemplate.update(
                """
                INSERT INTO retiros_cajero (
                    cuenta_id,
                    monto,
                    saldo_anterior,
                    saldo_posterior
                )
                VALUES (?, ?, ?, ?)
                """,
                cuentaId,
                monto,
                saldoActual,
                saldoPosterior
        );

        return new RetiroResponseDTO("cajero", cuentaId, monto, saldoActual, saldoPosterior);
    }
}