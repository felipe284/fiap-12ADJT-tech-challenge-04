package br.com.fiap.feedback.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DominioTest {

    @Test
    void classificaUrgenciaPelaNota() {
        assertEquals(Urgencia.CRITICA, Urgencia.fromNota(0));
        assertEquals(Urgencia.CRITICA, Urgencia.fromNota(3));
        assertEquals(Urgencia.MEDIA, Urgencia.fromNota(4));
        assertEquals(Urgencia.MEDIA, Urgencia.fromNota(6));
        assertEquals(Urgencia.BAIXA, Urgencia.fromNota(7));
        assertEquals(Urgencia.BAIXA, Urgencia.fromNota(10));
    }

    @Test
    void relatorioCalculaMediaEContagens() {
        Avaliacao critica = Avaliacao.nova("Aula confusa", 2);
        critica.dia = "2026-11-16";
        critica.dataEnvio = "2026-11-16T10:00:00Z";
        Avaliacao baixa = Avaliacao.nova("Boa aula", 8);
        baixa.dia = "2026-11-16";
        baixa.dataEnvio = "2026-11-16T11:00:00Z";

        Relatorio relatorio = Relatorio.de(List.of(critica, baixa));

        assertEquals(5.0, relatorio.media());
        assertEquals(1L, relatorio.porUrgencia().get(Urgencia.CRITICA));
        assertEquals(1L, relatorio.porUrgencia().get(Urgencia.BAIXA));
        assertEquals(0L, relatorio.porUrgencia().get(Urgencia.MEDIA));
        assertEquals(2L, relatorio.porDia().get("2026-11-16"));
        assertTrue(relatorio.toTexto().contains("Aula confusa | CRITICA | 2026-11-16T10:00:00Z"));
        assertTrue(relatorio.toTexto().contains("2026-11-16: 2"));
    }

    @Test
    void relatorioSemAvaliacoesTemMediaZero() {
        Relatorio relatorio = Relatorio.de(List.of());

        assertEquals(0.0, relatorio.media());
        assertTrue(relatorio.porDia().isEmpty());
        assertEquals(0L, relatorio.porUrgencia().get(Urgencia.CRITICA));
        assertTrue(relatorio.toTexto().contains("Total de avaliacoes: 0"));
    }
}
