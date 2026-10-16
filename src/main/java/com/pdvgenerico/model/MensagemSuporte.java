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

    /** "Apagar conversa" é por usuário: cada lado esconde só o que é seu. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean apagadaRemetente = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean apagadaDestinatario = false;
}
