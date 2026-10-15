package com.pdvgenerico.dto;

/**
 * Pessoa com quem o usuário logado pode conversar.
 *
 * @param online    teve atividade recente no sistema (ver PresencaService)
 * @param naoLidas  mensagens dessa pessoa que o usuário logado ainda não leu
 */
public record ContatoSuporteResponse(
        Long id,
        String nome,
        String login,
        String perfil,
        boolean online,
        long naoLidas
) {
}
