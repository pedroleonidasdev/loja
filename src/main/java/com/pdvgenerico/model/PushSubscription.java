package com.pdvgenerico.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Uma inscrição de notificação push do navegador (Web Push API) de um
 * usuário. Cada navegador/dispositivo em que o usuário clicar em "Ativar
 * notificações" gera um registro diferente (endpoint único), então uma
 * mesma pessoa pode receber a notificação no celular E no computador, por
 * exemplo.
 */
@Entity
@Table(name = "push_subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // URL única que identifica esse navegador/dispositivo junto ao serviço de
    // push (FCM, Mozilla, etc.) — é o que a Web Push API do navegador gera.
    @Column(nullable = false, unique = true, length = 500)
    private String endpoint;

    // Chaves públicas de criptografia da inscrição, exigidas pelo protocolo Web Push
    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    @Column(nullable = false)
    private LocalDateTime criadoEm;
}
