package com.pdvgenerico.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Conteúdo de uma mensagem de voz. Fica numa tabela à parte para que listar as
 * mensagens da conversa nunca carregue os bytes do áudio.
 */
@Entity
@Table(name = "audios_privados", indexes = @Index(name = "idx_audio_mensagem", columnList = "mensagemId", unique = true))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioPrivado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mensagemId;

    /** audio/mp4, audio/webm ou audio/ogg. */
    @Column(nullable = false, length = 40)
    private String tipoMime;

    @Column(nullable = false, columnDefinition = "longblob")
    private byte[] dados;

    @Column(nullable = false)
    private Instant criadaEm;
}
