package br.com.fiap.feedback.function;

import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.Urgencia;
import br.com.fiap.feedback.infra.AvaliacaoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.QueueOutput;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

public class ReceberAvaliacaoFunction {

    @Inject
    AvaliacaoRepository repository;

    @Inject
    ObjectMapper mapper;

    @FunctionName("receberAvaliacao")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", route = "avaliacao", methods = HttpMethod.POST,
                    authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
            @QueueOutput(name = "criticas", queueName = "avaliacoes-criticas",
                    connection = "AzureWebJobsStorage") OutputBinding<String> filaCriticas,
            ExecutionContext context) {

        JsonNode json;
        try {
            json = mapper.readTree(request.getBody().orElse(""));
        } catch (JsonProcessingException e) {
            return erro(request, "Corpo da requisicao deve ser um JSON valido");
        }
        if (json == null || !json.hasNonNull("descricao") || !json.get("descricao").isTextual()
                || json.get("descricao").asText().isBlank() || json.get("descricao").asText().length() > 2000) {
            return erro(request, "'descricao' e obrigatoria (texto de ate 2000 caracteres)");
        }
        if (!json.hasNonNull("nota") || !json.get("nota").isInt()
                || json.get("nota").asInt() < 0 || json.get("nota").asInt() > 10) {
            return erro(request, "'nota' e obrigatoria e deve ser um inteiro de 0 a 10");
        }

        Avaliacao avaliacao = Avaliacao.nova(json.get("descricao").asText().trim(), json.get("nota").asInt());
        try {
            repository.salvar(avaliacao);
            if (avaliacao.urgencia == Urgencia.CRITICA) {
                filaCriticas.setValue(mapper.writeValueAsString(avaliacao));
            }
        } catch (Exception e) {
            context.getLogger().severe("Falha ao registrar avaliacao: " + e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(Map.of("erro", "Falha ao registrar avaliacao")).build();
        }

        context.getLogger().info("Avaliacao registrada id=" + avaliacao.id + " urgencia=" + avaliacao.urgencia);
        return request.createResponseBuilder(HttpStatus.CREATED)
                .header("Content-Type", "application/json")
                .body(Map.of("id", avaliacao.id, "urgencia", avaliacao.urgencia.name())).build();
    }

    private HttpResponseMessage erro(HttpRequestMessage<?> request, String mensagem) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Map.of("erro", mensagem)).build();
    }
}
