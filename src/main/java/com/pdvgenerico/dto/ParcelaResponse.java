package com.pdvgenerico.dto;

import com.pdvgenerico.model.Parcela;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaResponse(
        Integer numero,
        LocalDate dataVencimento,
        BigDecimal valor
) {
    public static ParcelaResponse fromEntity(Parcela parcela) {
        return new ParcelaResponse(parcela.getNumero(), parcela.getDataVencimento(), parcela.getValor());
    }
}
