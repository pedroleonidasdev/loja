package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

// data de vencimento é opcional — permite lançar as parcelas sem já ter
// certeza de todas as datas (o usuário completa depois editando).
public record ParcelaRequest(
        LocalDate dataVencimento,

        @NotNull @Positive(message = "Valor da parcela deve ser maior que zero")
        BigDecimal valor
) {
}
