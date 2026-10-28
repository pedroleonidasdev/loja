package com.pdvgenerico.dto;

import com.pdvgenerico.model.MensagemSuporte;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record MensagemSuporteResponse(
        Long id,
        Long remetenteId,
        Long destinatarioId,
        String texto,
        Instant criadaEm,
        boolean lida,
        String tipo,
        Resposta respostaA,
        List<Reacao> reacoes,
        boolean editada,
        boolean apagadaParaTodos
) {
    /** Resumo da mensagem citada (mostrado dentro do balão de quem respondeu). */
    public record Resposta(Long id, Long remetenteId, String previa) {
    }

    public record Reacao(Long usuarioId, String emoji) {
    }

    public static MensagemSuporteResponse fromEntity(MensagemSuporte m) {
        return fromEntity(m, null);
    }

    public static MensagemSuporteResponse fromEntity(MensagemSuporte m, Resposta respostaA) {
        List<Reacao> reacoes = new ArrayList<>();
        if (m.getReacaoRemetente() != null && !m.getReacaoRemetente().isBlank()) {
            reacoes.add(new Reacao(m.getRemetenteId(), m.getReacaoRemetente()));
        }
        if (m.getReacaoDestinatario() != null && !m.getReacaoDestinatario().isBlank()) {
            reacoes.add(new Reacao(m.getDestinatarioId(), m.getReacaoDestinatario()));
        }
        return new MensagemSuporteResponse(
                m.getId(), m.getRemetenteId(), m.getDestinatarioId(),
                m.isApagadaParaTodos() ? "" : m.getTexto(),
                m.getCriadaEm(), m.isLida(), m.getTipo(),
                respostaA, reacoes, m.isEditada(), m.isApagadaParaTodos());
    }
}
