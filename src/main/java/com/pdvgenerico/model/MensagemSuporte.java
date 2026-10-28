package com.pdvgenerico.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mensagem privada entre dois usuários do sistema. Só o remetente e o
 * destinatário enxergam o conteúdo: toda consulta filtra pelo usuário logado.
 */
@Entity
@Table(name = "mensagens_privadas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MensagemSuporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long remetenteId;

    @Column(nullable = false)
    private Long destinatarioId;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(nullable = false)
    private Instant criadaEm;

    @Column(nullable = false)
    @Builder.Default
    private boolean lida = false;

    /**
     * Tipo da mensagem. Nulo = mensagem de texto comum; "CHAMADA" = registro
     * automático de uma chamada de áudio/vídeo (feita, perdida, recusada...).
     */
    @Column(length = 20)
    private String tipo;

    /** Mensagem à qual esta responde (citação, como no WhatsApp); nulo se não for resposta. */
    @Column(name = "resposta_a_id")
    private Long respostaAId;

    /** Reação (emoji) de cada lado a esta mensagem; nulo = sem reação. */
    @Column(length = 16)
    private String reacaoRemetente;

    @Column(length = 16)
    private String reacaoDestinatario;

    /** Texto alterado depois de enviado ("editada"). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean editada = false;

    /** "Apagar para todos": o conteúdo some dos dois lados e fica só o aviso "Mensagem apagada". */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean apagadaParaTodos = false;

    /** "Apagar conversa" é por usuário: cada lado esconde só o que é seu. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean apagadaRemetente = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean apagadaDestinatario = false;
}
