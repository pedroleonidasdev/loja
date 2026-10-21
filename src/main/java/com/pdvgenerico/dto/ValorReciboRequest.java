package com.pdvgenerico.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Valor do recibo de uma consignação já fechada (R$ 0,00 é permitido). Esse valor vai para o Faturamento. */
public record ValorReciboRequest(
        @NotNull(message = "Informe o valor do recibo")
        @DecimalMin(value = "0.00", message = "Valor do recibo não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Valor do recibo inválido")
        BigDecimal valorRecibo
) {
}
