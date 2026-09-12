package com.pdvgenerico.dto;

import com.pdvgenerico.model.FormaPagamento;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Edição de um lançamento já registrado. O tipo (DESPESA/SANGRIA/SUPRIMENTO)
// não muda depois de criado — mexe com o vínculo do lançamento com o caixa e
// com a conferência de gaveta em Relatórios, então fica de fora daqui.
public record EditarDespesaRequest(
        @Size(max = 60, message = "Categoria deve ter no máximo 60 caracteres")
        String categoria,

        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres")
        String descricao,

        @NotNull @Positive(message = "Valor deve ser maior que zero")
        BigDecimal valor,

        // Ignorada para SANGRIA/SUPRIMENTO — o backend sempre mantém DINHEIRO.
        FormaPagamento formaPagamento,

        @Min(value = 1, message = "Número de parcelas deve ser pelo menos 1")
        Integer numeroParcelas
) {
}
