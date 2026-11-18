package br.com.fiap.feedback.function;

import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.infra.AvaliacaoRepository;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ReceberAvaliacaoQuarkusTest {

    @Inject
    ReceberAvaliacaoFunction function;

    @InjectMock
    AvaliacaoRepository repository;

    @SuppressWarnings("unchecked")
    @Test
    void shouldRunFunctionWithInjectedDependencies() {
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        OutputBinding<String> fila = mock(OutputBinding.class);
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getLogger("teste"));
        HttpTestHelper.prepararRequisicao(request, "{\"descricao\":\"Aula confusa\",\"nota\":1}");

        HttpResponseMessage resposta = function.run(request, fila, context);

        assertEquals(HttpStatus.CREATED, resposta.getStatus());
        verify(repository).salvar(any(Avaliacao.class));
        verify(fila).setValue(anyString());
    }
}
