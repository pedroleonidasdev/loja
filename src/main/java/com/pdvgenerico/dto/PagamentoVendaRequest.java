package com.pdvgenerico.dto;

import com.pdvgenerico.model.FormaPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PagamentoVendaRequest(
        @NotNull FormaPagamento formaPagamento,
        @NotNull @Positive BigDecimal valor
) {}
