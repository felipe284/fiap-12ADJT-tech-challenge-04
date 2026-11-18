package br.com.fiap.feedback.function;

import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.infra.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import jakarta.inject.Inject;

public class NotificarCriticoFunction {

    @Inject
    EmailService emailService;

    @Inject
    ObjectMapper mapper;

    @FunctionName("notificarCritico")
    public void run(
            @QueueTrigger(name = "msg", queueName = "avaliacoes-criticas",
                    connection = "AzureWebJobsStorage") String mensagem,
            ExecutionContext context) throws Exception {

        Avaliacao avaliacao = mapper.readValue(mensagem, Avaliacao.class);
        String texto = "Uma avaliacao critica foi recebida.\n\n"
                + "Descricao: " + avaliacao.descricao + "\n"
                + "Urgencia: " + avaliacao.urgencia + "\n"
                + "Data de envio: " + avaliacao.dataEnvio + "\n";
        emailService.enviarParaAdmin("[URGENTE] Avaliacao critica recebida", texto);
        context.getLogger().info("Notificacao enviada para avaliacao id=" + avaliacao.id);
    }
}
