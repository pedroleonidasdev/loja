package com.pdvgenerico.dto;

import com.pdvgenerico.model.MensagemSuporte;

import java.time.Instant;

public record MensagemSuporteResponse(
        Long id,
        Long remetenteId,
        Long destinatarioId,
        String texto,
        Instant criadaEm,
        boolean lida
) {
    public static MensagemSuporteResponse fromEntity(MensagemSuporte m) {
        return new MensagemSuporteResponse(
                m.getId(), m.getRemetenteId(), m.getDestinatarioId(), m.getTexto(), m.getCriadaEm(), m.isLida());
    }
}
