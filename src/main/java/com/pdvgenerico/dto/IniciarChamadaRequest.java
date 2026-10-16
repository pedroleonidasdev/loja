package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param tipo   "AUDIO" ou "VIDEO"
 * @param oferta descrição de sessão WebRTC (SDP offer, em JSON) gerada pelo navegador de quem liga
 */
public record IniciarChamadaRequest(
        @NotNull Long destinatarioId,
        @NotBlank String tipo,
        @NotBlank @Size(max = 60000) String oferta
) {
}
