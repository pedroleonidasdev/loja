package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param tipo        opcional: nulo = mensagem de texto; "FIGURINHA" = figurinha (o texto
 *                    é o conteúdo dela). Qualquer outro valor é recusado pelo serviço.
 * @param respostaAId opcional: id da mensagem que está sendo respondida (citação)
 */
public record MensagemSuporteRequest(
        @NotBlank @Size(max = 1000) String texto,
        @Size(max = 20) String tipo,
        Long respostaAId
) {
}
