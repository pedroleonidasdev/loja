package com.pdvgenerico.dto;

import com.pdvgenerico.model.FormaPagamento;
import com.pdvgenerico.model.TipoDespesa;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record DespesaRequest(
        @NotNull(message = "Informe o tipo: despesa, sangria ou suprimento")
        TipoDespesa tipo,

        @Size(max = 60, message = "Categoria deve ter no máximo 60 caracteres")
        String categoria,

        @Size(max = 120, message = "Nome do fornecedor deve ter no máximo 120 caracteres")
        String fornecedor,

        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres")
        String descricao,

        @NotNull @Positive(message = "Valor deve ser maior que zero")
        BigDecimal valor,

        // Obrigatória apenas para tipo=DESPESA. Para SANGRIA/SUPRIMENTO o backend
        // sempre força DINHEIRO, independente do que vier aqui — dinheiro é a
        // única forma que sai/entra fisicamente na gaveta.
        FormaPagamento formaPagamento,

        // Opcional: em quantas vezes a despesa foi parcelada (ex: cheque em 5x).
        // Nulo/ausente = à vista. Ignorado quando `parcelas` vem preenchido —
        // nesse caso o número de parcelas é parcelas.size().
        @Min(value = 1, message = "Número de parcelas deve ser pelo menos 1")
        Integer numeroParcelas,

        // Opcional: detalhamento de cada parcela (data de vencimento + valor).
        @Valid
        List<ParcelaRequest> parcelas
) {
}
