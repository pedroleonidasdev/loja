package com.pdvgenerico.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Conteúdo de um arquivo anexado numa mensagem do chat. Fica numa tabela à parte para que
 * listar as mensagens da conversa nunca carregue os bytes. O nome do arquivo não é guardado
 * aqui: ele vai no texto da própria mensagem (coluna já preparada para acentos e emoji).
 */
@Entity
@Table(name = "anexos_privados", indexes = @Index(name = "idx_anexo_mensagem", columnList = "mensagemId", unique = true))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnexoPrivado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mensagemId;

    /** image/png, image/jpeg, image/gif, image/webp ou application/octet-stream (qualquer outro formato). */
    @Column(nullable = false, length = 40)
    private String tipoMime;

    /** Tamanho em bytes (para somar o espaço usado sem carregar os arquivos). */
    @Column(nullable = false)
    private Long tamanho;

    @Column(nullable = false, columnDefinition = "longblob")
    private byte[] dados;

    @Column(nullable = false)
    private Instant criadaEm;
}
