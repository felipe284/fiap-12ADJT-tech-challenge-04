package br.com.fiap.feedback.function;

import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.infra.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificarCriticoFunctionTest {

    @Mock
    private EmailService emailService;
    @Mock
    private ExecutionContext context;

    private final ObjectMapper mapper = new ObjectMapper();
    private NotificarCriticoFunction function;

    @BeforeEach
    void preparar() {
        function = new NotificarCriticoFunction();
        function.emailService = emailService;
        function.mapper = mapper;
        lenient().when(context.getLogger()).thenReturn(Logger.getLogger("teste"));
    }

    @Test
    void shouldSendEmailWithDescriptionUrgencyAndDate() throws Exception {
        Avaliacao avaliacao = Avaliacao.nova("Aula confusa", 1);
        String mensagem = mapper.writeValueAsString(avaliacao);

        function.run(mensagem, context);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarParaAdmin(eq("[URGENTE] Avaliacao critica recebida"), texto.capture());
        assertTrue(texto.getValue().contains("Descricao: Aula confusa"));
        assertTrue(texto.getValue().contains("Urgencia: CRITICA"));
        assertTrue(texto.getValue().contains("Data de envio: " + avaliacao.dataEnvio));
    }

    @Test
    void shouldFailWhenMessageIsInvalidSoTheQueueCanRetry() {
        assertThrows(Exception.class, () -> function.run("{invalid json", context));

        verify(emailService, never()).enviarParaAdmin(anyString(), anyString());
    }

    @Test
    void shouldFailWhenEmailSendingFailsSoTheQueueCanRetry() throws Exception {
        String mensagem = mapper.writeValueAsString(Avaliacao.nova("Aula ruim", 0));
        doThrow(new RuntimeException("email error")).when(emailService).enviarParaAdmin(anyString(), anyString());

        assertThrows(RuntimeException.class, () -> function.run(mensagem, context));
    }
}
