package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param resposta descrição de sessão WebRTC (SDP answer, em JSON) de quem atendeu */
public record AtenderChamadaRequest(
        @NotBlank @Size(max = 60000) String resposta
) {
}
