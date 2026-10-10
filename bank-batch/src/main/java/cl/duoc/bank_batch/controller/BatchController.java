package cl.duoc.bank_batch.controller;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private static final int MAX_INTENTOS = 3;

    private final JobOperator jobOperator;
    private final Map<String, Job> jobs;   // Spring inyecta todos los Job por nombre de bean
    private final JobRepository jobRepository;

    public BatchController(JobOperator jobOperator, Map<String, Job> jobs, JobRepository jobRepository) {
        this.jobOperator = jobOperator;
        this.jobs = jobs;
        this.jobRepository = jobRepository;
    }

    // POST /api/batch/transaccionJob | interesJob | estadoCuentaJob
    @PostMapping("/{nombre}")
    public ResponseEntity<Map<String, Object>> ejecutar(@PathVariable String nombre) throws Exception {

        Job job = jobs.get(nombre);
        if (job == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Job no existe: " + nombre));
        }

        JobExecution ejecucion = jobOperator.start(job, nuevosParametros());

        return ResponseEntity.ok(Map.of(
                "job", nombre,
                "executionId", ejecucion.getId(),
                "estado", ejecucion.getStatus().toString(),
                "exitCode", ejecucion.getExitStatus().getExitCode()
        ));
    }

    // POST /api/batch/{nombre}/auto  -> reejecucion automatica ante fallos criticos
    @PostMapping("/{nombre}/auto")
    public ResponseEntity<Map<String, Object>> ejecutarConReintento(@PathVariable String nombre) throws Exception {

        Job job = jobs.get(nombre);
        if (job == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Job no existe: " + nombre));
        }

        JobExecution ej = esperarTermino(jobOperator.start(job, nuevosParametros()));
        int intentos = 1;

        // Si el job falla, se reanuda desde el step que fallo (maximo 3 intentos)
        while (ej.getStatus() == BatchStatus.FAILED && intentos < MAX_INTENTOS) {
            ej = esperarTermino(jobOperator.restart(ej));
            intentos++;
        }

        return ResponseEntity.ok(Map.of(
                "job", nombre,
                "executionId", ej.getId(),
                "estado", ej.getStatus().toString(),
                "exitCode", ej.getExitStatus().getExitCode(),
                "intentos", intentos
        ));
    }

    private JobParameters nuevosParametros() {
        return new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
    }

    // Espera a que el job termine (por si corre en segundo plano)
    private JobExecution esperarTermino(JobExecution ej) throws InterruptedException {
        while (ej.isRunning()) {
            Thread.sleep(500);
            ej = jobRepository.getJobExecution(ej.getId());
        }
        return ej;
    }
}