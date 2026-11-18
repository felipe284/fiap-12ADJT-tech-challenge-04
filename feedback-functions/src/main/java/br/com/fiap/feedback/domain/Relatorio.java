package br.com.fiap.feedback.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Relatorio(
        List<Avaliacao> avaliacoes,
        double media,
        Map<String, Long> porDia,
        Map<Urgencia, Long> porUrgencia) {

    public static Relatorio de(List<Avaliacao> avaliacoes) {
        double somaNotas = 0;
        Map<String, Long> porDia = new TreeMap<>();
        Map<Urgencia, Long> porUrgencia = new EnumMap<>(Urgencia.class);
        for (Urgencia urgencia : Urgencia.values()) {
            porUrgencia.put(urgencia, 0L);
        }
        for (Avaliacao avaliacao : avaliacoes) {
            somaNotas += avaliacao.nota;
            long quantidadeNoDia = porDia.getOrDefault(avaliacao.dia, 0L);
            porDia.put(avaliacao.dia, quantidadeNoDia + 1);
            long quantidadeNaUrgencia = porUrgencia.get(avaliacao.urgencia);
            porUrgencia.put(avaliacao.urgencia, quantidadeNaUrgencia + 1);
        }
        double media = 0;
        if (!avaliacoes.isEmpty()) {
            media = somaNotas / avaliacoes.size();
        }

        return new Relatorio(avaliacoes, media, porDia, porUrgencia);
    }

    public String toTexto() {
        StringBuilder texto = new StringBuilder();
        texto.append("RELATORIO SEMANAL DE AVALIACOES\n\n");
        texto.append("Total de avaliacoes: ").append(avaliacoes.size()).append('\n');
        texto.append(String.format("Media das notas: %.2f%n%n", media));
        texto.append("Quantidade por dia:\n");
        for (Map.Entry<String, Long> dia : porDia.entrySet()) {
            texto.append("  ").append(dia.getKey()).append(": ").append(dia.getValue()).append('\n');
        }
        texto.append("\nQuantidade por urgencia:\n");
        for (Map.Entry<Urgencia, Long> urgencia : porUrgencia.entrySet()) {
            texto.append("  ").append(urgencia.getKey()).append(": ").append(urgencia.getValue()).append('\n');
        }
        texto.append("\nAvaliacoes (descricao | urgencia | data de envio):\n");
        for (Avaliacao avaliacao : avaliacoes) {
            texto.append("  - ").append(avaliacao.descricao).append(" | ")
                    .append(avaliacao.urgencia).append(" | ").append(avaliacao.dataEnvio).append('\n');
        }
        return texto.toString();
    }
}
