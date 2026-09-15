package com.pdvgenerico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma parcela individual de uma despesa parcelada (ex: cada cheque de um
 * pagamento em 5x). numero é a posição (1, 2, 3...), dataVencimento é opcional
 * — o usuário pode lançar as parcelas sem se comprometer com uma data ainda.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parcela {

    private Integer numero;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    @Builder.Default
    private Boolean pago = false;
}
