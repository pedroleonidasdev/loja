package com.pdvgenerico.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record FecharConsignacaoRequest(
        @NotEmpty(message = "Informe a quantidade vendida de cada item")
        @Valid
        List<ItemVendido> itens,

        /** Opcional: valor do recibo editado na tela de fechamento (R$ 0,00 é permitido). Vai para o Faturamento. */
        @DecimalMin(value = "0.00", message = "Valor do recibo não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Valor do recibo inválido")
        BigDecimal valorRecibo
) {
    public record ItemVendido(
            @NotNull Long itemId,
            @NotNull @Min(value = 0, message = "Quantidade vendida não pode ser negativa") Integer quantidadeVendida,

            /** Opcional: corrige o preço desta linha no acerto (só desta consignação; R$ 0,00 é permitido). */
            @DecimalMin(value = "0.00", message = "Preço não pode ser negativo")
            @Digits(integer = 8, fraction = 2, message = "Preço inválido")
            BigDecimal precoUnitario
    ) {
    }
}
