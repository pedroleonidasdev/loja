package com.pdvgenerico.dto;

/**
 * Estado da outra pessoa na conversa aberta.
 *
 * @param digitando          está digitando para mim agora
 * @param online             teve atividade recente no sistema
 * @param ultimoAcessoHaSeg  segundos desde a última atividade (para "visto por último"); null se desconhecido
 */
public record StatusConversaResponse(boolean digitando, boolean online, Long ultimoAcessoHaSeg) {
}
