package cl.duoc.bank_batch.config;

import javax.sql.DataSource;

import cl.duoc.bank_batch.model.MovimientoAnual;
import cl.duoc.bank_batch.processor.MovimientoAnualProcessor;
import cl.duoc.bank_batch.policy.CustomSkipPolicy;
import cl.duoc.bank_batch.policy.DataQualityDecider;

import org.springframework.core.task.AsyncTaskExecutor;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;

import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;

import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;

import org.springframework.batch.infrastructure.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.infrastructure.item.support.builder.SynchronizedItemStreamReaderBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.io.ClassPathResource;

import org.springframework.dao.TransientDataAccessException;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class EstadoCuentaJobConfig {

    // -------------------------------------------------
    // READER
    // -------------------------------------------------

    @Bean
    public SynchronizedItemStreamReader<MovimientoAnual>
            movimientoAnualReader() {

        FlatFileItemReader<MovimientoAnual> delegate =
                new FlatFileItemReaderBuilder<MovimientoAnual>()
                        .name("movimientoAnualReader")
                        .resource(
                                new ClassPathResource(
                                        "data/cuentas_anuales.csv"
                                )
                        )
                        .linesToSkip(1)
                        .delimited()
                        .names(
                                "cuentaId",
                                "fecha",
                                "transaccion",
                                "monto",
                                "descripcion"
                        )
                        .targetType(MovimientoAnual.class)
                        .build();

        return new SynchronizedItemStreamReaderBuilder<MovimientoAnual>()
                .delegate(delegate)
                .build();
    }

    // -------------------------------------------------
    // WRITER
    // -------------------------------------------------

    @Bean
    public JdbcBatchItemWriter<MovimientoAnual>
            movimientoAnualWriter(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<MovimientoAnual>()
                .dataSource(dataSource)
                .sql("""
                    INSERT INTO movimientos_anuales
                    (
                        cuenta_id,
                        fecha,
                        transaccion,
                        monto,
                        descripcion,
                        anomalia,
                        detalle_anomalia
                    )
                    VALUES
                    (
                        :cuentaId,
                        CAST(:fecha AS DATE),
                        :transaccion,
                        :monto,
                        :descripcion,
                        :anomalia,
                        :detalleAnomalia
                    )
                    ON CONFLICT (
                        cuenta_id,
                        fecha,
                        transaccion,
                        monto
                    )
                    DO UPDATE SET
                        descripcion = EXCLUDED.descripcion,
                        anomalia = EXCLUDED.anomalia,
                        detalle_anomalia = EXCLUDED.detalle_anomalia
                    """)
                .beanMapped()
                .build();
    }

    // -------------------------------------------------
    // STEP MOVIMIENTOS
    // -------------------------------------------------

    @Bean
    public Step movimientoAnualStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SynchronizedItemStreamReader<MovimientoAnual>
                    movimientoAnualReader,
            MovimientoAnualProcessor movimientoAnualProcessor,
            CustomSkipPolicy customSkipPolicy,
            AsyncTaskExecutor taskExecutor,
            JdbcBatchItemWriter<MovimientoAnual>
                    movimientoAnualWriter) {

        return new StepBuilder(
                "movimientoAnualStep",
                jobRepository
        )
                .<MovimientoAnual, MovimientoAnual>chunk(100)
                .transactionManager(transactionManager)

                .reader(movimientoAnualReader)
                .processor(movimientoAnualProcessor)
                .writer(movimientoAnualWriter)

                .faultTolerant()

                .retry(TransientDataAccessException.class)
                .retryLimit(3)

                .skipPolicy(customSkipPolicy)

                .taskExecutor(taskExecutor)

                .build();
    }

    // -------------------------------------------------
    // STEP RESUMEN ANUAL
    // -------------------------------------------------

    @Bean
    public Step resumenAnualStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            DataSource dataSource) {

        JdbcTemplate jdbcTemplate =
                new JdbcTemplate(dataSource);

        Tasklet tasklet = (contribution, chunkContext) -> {

            jdbcTemplate.update("""
                INSERT INTO resumen_anual
                (
                    cuenta_id,
                    total_movimientos,
                    total_ingresos,
                    total_egresos,
                    saldo_anual,
                    total_anomalias
                )
                SELECT
                    cuenta_id,
                    COUNT(*) AS total_movimientos,

                    SUM(
                        CASE
                            WHEN monto > 0
                            THEN monto
                            ELSE 0
                        END
                    ),

                    SUM(
                        CASE
                            WHEN monto < 0
                            THEN ABS(monto)
                            ELSE 0
                        END
                    ),

                    SUM(monto),

                    COUNT(*) FILTER (
                        WHERE anomalia = true
                    )

                FROM movimientos_anuales

                GROUP BY cuenta_id

                ON CONFLICT (cuenta_id)
                DO UPDATE SET

                    total_movimientos =
                        EXCLUDED.total_movimientos,

                    total_ingresos =
                        EXCLUDED.total_ingresos,

                    total_egresos =
                        EXCLUDED.total_egresos,

                    saldo_anual =
                        EXCLUDED.saldo_anual,

                    total_anomalias =
                        EXCLUDED.total_anomalias
                """);

            System.out.println(
                    "Resumen anual generado correctamente"
            );

            return null;
        };

        return new StepBuilder(
                "resumenAnualStep",
                jobRepository
        )
                .tasklet(
                        tasklet,
                        transactionManager
                )
                .build();
    }

    // -------------------------------------------------
    // JOB
    // -------------------------------------------------

    @Bean
    public Job estadoCuentaJob(
            JobRepository jobRepository,
            Step movimientoAnualStep,
            Step resumenAnualStep,
            DataQualityDecider dataQualityDecider) {

        return new JobBuilder(
                "estadoCuentaJob",
                jobRepository
        )
                .start(movimientoAnualStep)

                .next(dataQualityDecider)
                    .on(
                        DataQualityDecider
                            .CALIDAD_INSUFICIENTE
                    )
                    .fail()

                .from(dataQualityDecider)
                    .on("*")
                    .to(resumenAnualStep)

                .end()
                .build();
    }
}