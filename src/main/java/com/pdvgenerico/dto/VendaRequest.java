package com.pdvgenerico.dto;

import com.pdvgenerico.model.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequest(
        @NotNull FormaPagamento formaPagamento,
        @DecimalMin(value = "0.0", message = "Desconto não pode ser negativo")
        @DecimalMax(value = "100.0", message = "Desconto não pode ser maior que 100%")
        BigDecimal percentualDesconto,
        @NotEmpty @Valid List<ItemVendaRequest> itens
) {
    public record ItemVendaRequest(
            @NotNull Long produtoId,
            @NotNull Integer quantidade
    ) {
    }
}
