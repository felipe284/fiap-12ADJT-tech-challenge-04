package br.com.fiap.feedback.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Avaliacao {

    public String id;
    public String descricao;
    public int nota;
    public Urgencia urgencia;
    public String dataEnvio;
    public String dia;

    public Avaliacao() {
    }

    public static Avaliacao nova(String descricao, int nota) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.id = UUID.randomUUID().toString();
        avaliacao.descricao = descricao;
        avaliacao.nota = nota;
        avaliacao.urgencia = Urgencia.fromNota(nota);
        avaliacao.dataEnvio = agora.toString();
        avaliacao.dia = agora.toLocalDate().toString();
        return avaliacao;
    }
}
