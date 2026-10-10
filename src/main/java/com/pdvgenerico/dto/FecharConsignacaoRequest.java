package com.pdvgenerico.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FecharConsignacaoRequest(
        @NotEmpty(message = "Informe a quantidade vendida de cada item")
        @Valid
        List<ItemVendido> itens
) {
    public record ItemVendido(
            @NotNull Long itemId,
            @NotNull @Min(value = 0, message = "Quantidade vendida não pode ser negativa") Integer quantidadeVendida
    ) {
    }
}
