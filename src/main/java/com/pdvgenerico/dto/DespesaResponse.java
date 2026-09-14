package com.pdvgenerico.dto;

import com.pdvgenerico.model.Despesa;

import java.math.BigDecimal;
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
        String usuarioNome,
        Long caixaId,
        Integer numeroParcelas,
        List<ParcelaResponse> parcelas
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
                despesa.getUsuario().getNome(),
                despesa.getCaixa() != null ? despesa.getCaixa().getId() : null,
                despesa.getNumeroParcelas(),
                despesa.getParcelas().stream().map(ParcelaResponse::fromEntity).toList()
        );
    }
}
