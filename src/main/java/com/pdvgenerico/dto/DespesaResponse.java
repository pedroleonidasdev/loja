package com.pdvgenerico.dto;

import com.pdvgenerico.model.Despesa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DespesaResponse(
        Long id,
        String tipo,
        String categoria,
        String fornecedor,
        String descricao,
        BigDecimal valor,
        String formaPagamento,
        LocalDateTime dataHora,
        LocalDate dataVencimento,
        String usuarioNome,
        Long caixaId,
        Integer numeroParcelas,
        List<ParcelaResponse> parcelas,
        boolean pago
) {
    public static DespesaResponse fromEntity(Despesa despesa) {
        return new DespesaResponse(
                despesa.getId(),
                despesa.getTipo().name(),
                despesa.getCategoria(),
                despesa.getFornecedor(),
                despesa.getDescricao(),
                despesa.getValor(),
                despesa.getFormaPagamento().name(),
                despesa.getDataHora(),
                despesa.getDataVencimento(),
                despesa.getUsuario().getNome(),
                despesa.getCaixa() != null ? despesa.getCaixa().getId() : null,
                despesa.getNumeroParcelas(),
                despesa.getParcelas().stream().map(ParcelaResponse::fromEntity).toList(),
                // à vista: usa o campo da própria despesa. Parcelada: "paga" só quando
                // TODAS as parcelas estão pagas (é o que a UI usa pra marcar o lançamento
                // inteiro como quitado, mesmo controlando cada parcela por baixo).
                despesa.getParcelas().isEmpty()
                        ? Boolean.TRUE.equals(despesa.getPago())
                        : despesa.getParcelas().stream().allMatch(p -> Boolean.TRUE.equals(p.getPago()))
        );
    }
}
