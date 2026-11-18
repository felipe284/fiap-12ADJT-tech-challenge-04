package br.com.fiap.feedback.function;

import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.infra.AvaliacaoRepository;
import br.com.fiap.feedback.infra.EmailService;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioSemanalFunctionTest {

    @Mock
    private AvaliacaoRepository repository;
    @Mock
    private EmailService emailService;
    @Mock
    private ExecutionContext context;

    private RelatorioSemanalFunction function;

    @BeforeEach
    void preparar() {
        function = new RelatorioSemanalFunction();
        function.repository = repository;
        function.emailService = emailService;
        lenient().when(context.getLogger()).thenReturn(Logger.getLogger("teste"));
    }

    @Test
    void shouldSendReportWithTotalsAndRatings() {
        when(repository.buscarDesde(anyString()))
                .thenReturn(List.of(Avaliacao.nova("Aula confusa", 2), Avaliacao.nova("Otima aula", 8)));

        function.run("timer", context);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarParaAdmin(eq("Relatorio semanal de avaliacoes"), texto.capture());
        assertTrue(texto.getValue().contains("Total de avaliacoes: 2"));
        assertTrue(texto.getValue().contains("CRITICA: 1"));
        assertTrue(texto.getValue().contains("BAIXA: 1"));
        assertTrue(texto.getValue().contains("Aula confusa | CRITICA"));
    }

    @Test
    void shouldSendReportEvenWithoutRatings() {
        when(repository.buscarDesde(anyString())).thenReturn(List.of());

        function.run("timer", context);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarParaAdmin(eq("Relatorio semanal de avaliacoes"), texto.capture());
        assertTrue(texto.getValue().contains("Total de avaliacoes: 0"));
    }

    @Test
    void shouldQueryOnlyTheLastSevenDays() {
        when(repository.buscarDesde(anyString())).thenReturn(List.of());

        function.run("timer", context);

        ArgumentCaptor<String> inicio = ArgumentCaptor.forClass(String.class);
        verify(repository).buscarDesde(inicio.capture());
        OffsetDateTime esperado = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        long diferenca = Math.abs(Duration.between(OffsetDateTime.parse(inicio.getValue()), esperado).toSeconds());
        assertTrue(diferenca < 60);
    }

    @Test
    void shouldNotSendEmailWhenQueryFails() {
        when(repository.buscarDesde(anyString())).thenThrow(new RuntimeException("db error"));

        assertThrows(RuntimeException.class, () -> function.run("timer", context));

        verify(emailService, never()).enviarParaAdmin(anyString(), anyString());
    }
}
