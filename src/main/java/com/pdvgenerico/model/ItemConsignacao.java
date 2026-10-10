package com.pdvgenerico.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "itens_consignacao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemConsignacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consignacao_id", nullable = false)
    private Consignacao consignacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidadeEnviada;

    /** Preenchida no fechamento; 0 enquanto a consignação está aberta. */
    @Column(nullable = false)
    @Builder.Default
    private Integer quantidadeVendida = 0;

    /** Preço unitário do produto no momento da consignação (snapshot). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    /** Percentual de comissão do consignatário (15 ou 20), fixado ao criar. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualComissao;
}
