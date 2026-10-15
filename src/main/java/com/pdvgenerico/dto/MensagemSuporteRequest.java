package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensagemSuporteRequest(
        @NotBlank @Size(max = 1000) String texto
) {
}
