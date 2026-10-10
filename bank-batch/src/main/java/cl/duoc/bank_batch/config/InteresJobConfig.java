package cl.duoc.bank_batch.config;

import cl.duoc.bank_batch.model.CuentaInteres;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.infrastructure.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.TransientDataAccessException;

import javax.sql.DataSource;

import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import cl.duoc.bank_batch.processor.InteresProcessor;
import cl.duoc.bank_batch.policy.CustomSkipPolicy;
import cl.duoc.bank_batch.policy.DataQualityDecider;
import org.springframework.core.task.AsyncTaskExecutor;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class InteresJobConfig {

    @Bean
    public SynchronizedItemStreamReader<CuentaInteres> interesReader() {

        FlatFileItemReader<CuentaInteres> delegate = new FlatFileItemReaderBuilder<CuentaInteres>()
                .name("interesReader")
                .resource(new ClassPathResource("data/intereses.csv"))
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .targetType(CuentaInteres.class)
                .build();

        return new SynchronizedItemStreamReaderBuilder<CuentaInteres>()
                .delegate(delegate)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<CuentaInteres> interesWriter(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<CuentaInteres>()
                .dataSource(dataSource)
                .sql("""
                    INSERT INTO cuentas_intereses (
                        cuenta_id,
                        nombre,
                        saldo,
                        edad,
                        tipo,
                        tasa_interes,
                        interes_calculado,
                        saldo_final,
                        valida,
                        observacion
                    )
                    VALUES (
                        :cuentaId,
                        :nombre,
                        :saldo,
                        :edad,
                        :tipo,
                        :tasaInteres,
                        :interesCalculado,
                        :saldoFinal,
                        :valida,
                        :observacion
                    )
                    ON CONFLICT (cuenta_id) DO UPDATE SET
                        nombre = EXCLUDED.nombre,
                        saldo = EXCLUDED.saldo,
                        edad = EXCLUDED.edad,
                        tipo = EXCLUDED.tipo,
                        tasa_interes = EXCLUDED.tasa_interes,
                        interes_calculado = EXCLUDED.interes_calculado,
                        saldo_final = EXCLUDED.saldo_final,
                        valida = EXCLUDED.valida,
                        observacion = EXCLUDED.observacion
                    """)
                .beanMapped()
                .build();
    }

   @Bean
    public Step interesStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SynchronizedItemStreamReader<CuentaInteres> interesReader,
            InteresProcessor interesProcessor,
            JdbcBatchItemWriter<CuentaInteres> interesWriter,
            CustomSkipPolicy customSkipPolicy,
            AsyncTaskExecutor taskExecutor) {
                
        return new StepBuilder("interesStep", jobRepository)
                .<CuentaInteres, CuentaInteres>chunk(100)
                .transactionManager(transactionManager)
                .reader(interesReader)
                .processor(interesProcessor)
                .writer(interesWriter)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .skipPolicy(customSkipPolicy)
                .taskExecutor(taskExecutor)
                .build();
    }

    @Bean
    public Job interesJob(
            JobRepository jobRepository,
            Step interesStep,
            DataQualityDecider dataQualityDecider) {

        return new JobBuilder("interesJob", jobRepository)
                .start(interesStep)
                .next(dataQualityDecider).on(DataQualityDecider.CALIDAD_INSUFICIENTE).fail()
                .from(dataQualityDecider).on("*").end()
                .end()
                .build();
    }
}
