package com.pdvgenerico.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Espelha o formato que o navegador devolve em
 * {@code PushSubscription.toJSON()} no JavaScript (endpoint + keys.p256dh +
 * keys.auth) — por isso o formato "estranho" com um record aninhado "keys".
 */
public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        @Valid @NotNull Keys keys
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}
}
