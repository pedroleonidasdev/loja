package com.pdvgenerico.dto;

import java.time.Instant;

/**
 * Pessoa com quem o usuário logado pode conversar.
 *
 * @param online             teve atividade recente no sistema (ver PresencaService)
 * @param naoLidas           mensagens dessa pessoa que o usuário logado ainda não leu
 * @param digitando          a pessoa está digitando para o usuário logado agora
 * @param ultimaMensagem     prévia da última mensagem da conversa (null se não houver)
 * @param ultimaEm           quando a última mensagem foi enviada (null se não houver)
 * @param ultimoAcessoHaSeg  segundos desde a última atividade da pessoa (para "visto por último")
 */
public record ContatoSuporteResponse(
        Long id,
        String nome,
        String login,
        String perfil,
        boolean online,
        long naoLidas,
        boolean digitando,
        String ultimaMensagem,
        Instant ultimaEm,
        Long ultimoAcessoHaSeg
) {
}
