package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// dataHora chega do frontend já convertida para UTC "cru" (mesmo padrão usado
// em registrarVenda / limiteDiaBrasiliaParaUtc), então é gravada como veio.
public record EditarDataHoraRequest(
        @NotNull LocalDateTime dataHora
) {
}
