package com.pdvgenerico.dto;

import java.time.Instant;

/**
 * Estado de uma chamada do ponto de vista de quem consulta.
 *
 * @param direcao "SAIDA" (eu liguei) ou "ENTRADA" (estão me ligando)
 * @param status  TOCANDO, EM_ANDAMENTO, ENCERRADA, RECUSADA, NAO_ATENDIDA ou CANCELADA
 * @param oferta  SDP de quem ligou (útil para quem atende)
 * @param resposta SDP de quem atendeu (útil para quem ligou)
 */
public record ChamadaResponse(
        String id,
        String tipo,
        String status,
        String direcao,
        Long outroId,
        String outroNome,
        String oferta,
        String resposta,
        Instant criadaEm,
        Instant atendidaEm
) {
}
