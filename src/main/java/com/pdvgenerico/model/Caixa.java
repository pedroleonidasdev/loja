package com.pdvgenerico.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "caixas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Caixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_abertura_id", nullable = false)
    private Usuario usuarioAbertura;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorInicial;

    @Column(nullable = false)
    private LocalDateTime dataAbertura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_fechamento_id")
    private Usuario usuarioFechamento;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorFinal;

    private LocalDateTime dataFechamento;

    @Column(nullable = false)
    @Builder.Default
    private boolean aberto = true;
}
