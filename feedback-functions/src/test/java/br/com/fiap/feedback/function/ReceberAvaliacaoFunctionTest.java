package br.com.fiap.feedback.function;

import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.infra.AvaliacaoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceberAvaliacaoFunctionTest {

    @Mock
    private AvaliacaoRepository repository;
    @Mock
    private HttpRequestMessage<Optional<String>> request;
    @Mock
    private OutputBinding<String> filaCriticas;
    @Mock
    private ExecutionContext context;

    private ReceberAvaliacaoFunction function;

    @BeforeEach
    void preparar() {
        function = new ReceberAvaliacaoFunction();
        function.repository = repository;
        function.mapper = new ObjectMapper();
        lenient().when(context.getLogger()).thenReturn(Logger.getLogger("teste"));
    }

    @Test
    void shouldSaveAndQueueWhenRatingIsCritical() {
        HttpTestHelper.prepararRequisicao(request, "{\"descricao\":\"Aula confusa\",\"nota\":2}");

        HttpResponseMessage resposta = function.run(request, filaCriticas, context);

        assertEquals(HttpStatus.CREATED, resposta.getStatus());
        ArgumentCaptor<Avaliacao> captor = ArgumentCaptor.forClass(Avaliacao.class);
        verify(repository).salvar(captor.capture());
        assertEquals("Aula confusa", captor.getValue().descricao);
        assertEquals(2, captor.getValue().nota);
        verify(filaCriticas).setValue(anyString());
    }

    @Test
    void shouldSaveWithoutQueueWhenRatingIsGood() {
        HttpTestHelper.prepararRequisicao(request, "{\"descricao\":\"Otima aula\",\"nota\":9}");

        HttpResponseMessage resposta = function.run(request, filaCriticas, context);

        assertEquals(HttpStatus.CREATED, resposta.getStatus());
        verify(repository).salvar(any(Avaliacao.class));
        verify(filaCriticas, never()).setValue(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{invalid json",
            "",
            "{\"nota\":5}",
            "{\"descricao\":\"\",\"nota\":5}",
            "{\"descricao\":\"   \",\"nota\":5}",
            "{\"descricao\":\"Aula\"}",
            "{\"descricao\":\"Aula\",\"nota\":11}",
            "{\"descricao\":\"Aula\",\"nota\":-1}",
            "{\"descricao\":\"Aula\",\"nota\":5.5}",
            "{\"descricao\":\"Aula\",\"nota\":\"abc\"}",
            "{\"descricao\":123,\"nota\":5}"
    })
    void shouldReturnBadRequestWhenBodyIsInvalid(String corpo) {
        HttpTestHelper.prepararRequisicao(request, corpo);

        HttpResponseMessage resposta = function.run(request, filaCriticas, context);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatus());
        verify(repository, never()).salvar(any(Avaliacao.class));
        verify(filaCriticas, never()).setValue(anyString());
    }

    @Test
    void shouldAcceptLimitRatings() {
        HttpTestHelper.prepararRequisicao(request, "{\"descricao\":\"Aula\",\"nota\":0}");
        assertEquals(HttpStatus.CREATED, function.run(request, filaCriticas, context).getStatus());

        HttpTestHelper.prepararRequisicao(request, "{\"descricao\":\"Aula\",\"nota\":10}");
        assertEquals(HttpStatus.CREATED, function.run(request, filaCriticas, context).getStatus());
    }

    @Test
    void shouldReturnServerErrorWhenRepositoryFails() {
        HttpTestHelper.prepararRequisicao(request, "{\"descricao\":\"Aula\",\"nota\":8}");
        doThrow(new RuntimeException("db error")).when(repository).salvar(any(Avaliacao.class));

        HttpResponseMessage resposta = function.run(request, filaCriticas, context);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatus());
        verify(filaCriticas, never()).setValue(anyString());
    }
}
