package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditarMensagemRequest(@NotBlank @Size(max = 1000) String texto) {
}
