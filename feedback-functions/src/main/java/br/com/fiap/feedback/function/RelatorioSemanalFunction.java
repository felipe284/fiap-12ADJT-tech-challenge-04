package br.com.fiap.feedback.function;

import br.com.fiap.feedback.domain.Relatorio;
import br.com.fiap.feedback.infra.AvaliacaoRepository;
import br.com.fiap.feedback.infra.EmailService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class RelatorioSemanalFunction {

    @Inject
    AvaliacaoRepository repository;

    @Inject
    EmailService emailService;

    @FunctionName("relatorioSemanal")
    public void run(
            @TimerTrigger(name = "timer", schedule = "0 0 8 * * MON") String timerInfo,
            ExecutionContext context) {

        String desde = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toString();
        Relatorio relatorio = Relatorio.de(repository.buscarDesde(desde));
        emailService.enviarParaAdmin("Relatorio semanal de avaliacoes", relatorio.toTexto());
        context.getLogger().info("Relatorio semanal enviado. Total=" + relatorio.avaliacoes().size());
    }
}
