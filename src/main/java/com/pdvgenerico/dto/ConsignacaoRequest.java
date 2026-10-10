package com.pdvgenerico.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ConsignacaoRequest(
        @NotBlank(message = "Informe o consignatário")
        @Size(max = 150, message = "Consignatário deve ter no máximo 150 caracteres")
        String consignatario,

        LocalDate prazoDevolucao,

        @Size(max = 255, message = "Observação deve ter no máximo 255 caracteres")
        String observacao,

        @NotEmpty(message = "Informe ao menos um item")
        @Valid
        List<ItemConsignacaoRequest> itens
) {
    public record ItemConsignacaoRequest(
            @NotNull Long produtoId,
            @NotNull @Positive(message = "Quantidade deve ser maior que zero") Integer quantidade
    ) {
    }
}
