package com.pdvgenerico.dto;

import com.pdvgenerico.model.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registro de uma venda que aconteceu antes e não foi lançada (ex.: feita depois
 * do fechamento do caixa). Mesmos campos de uma venda normal, mais a data/hora
 * em que ela realmente aconteceu e, opcionalmente, quem foi o vendedor.
 *
 * dataHora chega do frontend já convertida para UTC "cru" (mesmo padrão de
 * EditarDataHoraRequest). usuarioId vazio = o próprio ADMIN que está registrando.
 */
public record VendaRetroativaRequest(
        @NotNull(message = "Informe a data e hora da venda") LocalDateTime dataHora,
        Long usuarioId,
        FormaPagamento formaPagamento,
        @DecimalMin(value = "0.0", message = "Desconto não pode ser negativo")
        @DecimalMax(value = "100.0", message = "Desconto não pode ser maior que 100%")
        BigDecimal percentualDesconto,
        @DecimalMin(value = "0.0", message = "Desconto não pode ser negativo")
        BigDecimal valorDescontoInformado,
        @Valid List<PagamentoVendaRequest> pagamentos,
        @NotEmpty @Valid List<VendaRequest.ItemVendaRequest> itens
) {
    public VendaRequest paraVendaRequest() {
        return new VendaRequest(formaPagamento, percentualDesconto, valorDescontoInformado, pagamentos, itens);
    }
}
