package com.pdvgenerico.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Espelha o formato que o navegador devolve em
 * {@code PushSubscription.toJSON()} no JavaScript (endpoint + keys.p256dh +
 * keys.auth) — por isso o formato "estranho" com um record aninhado "keys".
 */
public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        @Valid @NotNull Keys keys,
        // opcional: descrição do aparelho (ex.: "Celular Android (Chrome)"), só pra identificar
        // pra quem a notificação foi enviada no botão de teste
        @Size(max = 120) String dispositivo
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}
}
