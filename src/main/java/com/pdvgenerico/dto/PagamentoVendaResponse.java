package com.pdvgenerico.dto;

import com.pdvgenerico.model.PagamentoVenda;

import java.math.BigDecimal;

public record PagamentoVendaResponse(String formaPagamento, BigDecimal valor) {
    public static PagamentoVendaResponse fromEntity(PagamentoVenda p) {
        return new PagamentoVendaResponse(p.getFormaPagamento().name(), p.getValor());
    }
}
