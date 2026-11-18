package br.com.fiap.feedback.domain;

public enum Urgencia {
    CRITICA,
    MEDIA,
    BAIXA;

    public static Urgencia fromNota(int nota) {
        if (nota <= 3) {
            return CRITICA;
        }
        if (nota <= 6) {
            return MEDIA;
        }
        return BAIXA;
    }
}
