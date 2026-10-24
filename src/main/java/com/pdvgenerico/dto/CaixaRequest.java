package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CaixaRequest(
        @NotNull @PositiveOrZero BigDecimal valorInicial,
        // Login que a tela mostra como "usuário logado". Opcional (clientes antigos não
        // enviam). Quando vem, o servidor confere com o usuário do token e recusa a
        // abertura se forem pessoas diferentes, em vez de gravar a pessoa errada.
        String loginLogado
) {
    public record EdicaoAberturaRequest(
            @NotNull @PositiveOrZero BigDecimal valorInicial
    ) {
    }

    public record FechamentoRequest(
            @NotNull @PositiveOrZero BigDecimal valorFinal
    ) {
    }
}
