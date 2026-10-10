package com.pdvgenerico.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mercadoria entregue em consignação a um consignatário (ex: um Cursilho, uma
 * igreja ou um grupo), conforme o "Termo de Responsabilidade" e o "Relatório
 * de Mercadorias em Consignação" impressos pela loja.
 *
 * Regra de estoque: enquanto a consignação está ABERTA o estoque não muda.
 * Ao FECHAR (acerto), só a quantidade efetivamente vendida dá baixa; o que não
 * foi vendido é devolvido à loja e, como nunca saiu do estoque do sistema,
 * simplesmente permanece nele.
 */
@Entity
@Table(name = "consignacoes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consignacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Instante de criação (UTC "cru", mesmo padrão de vendas e trocas). */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime dataHora = LocalDateTime.now();

    /** Quem recebeu a mercadoria, ex: "Cursilho Feminino - 2026". */
    @Column(nullable = false, length = 150)
    private String consignatario;

    /** Data combinada para devolver o que não for vendido. */
    @Column
    private LocalDate prazoDevolucao;

    @Column(length = 255)
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private StatusConsignacao status = StatusConsignacao.ABERTA;

    @Column
    private LocalDateTime dataFechamento;

    @OneToMany(mappedBy = "consignacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ItemConsignacao> itens = new ArrayList<>();
}
