package com.pdvgenerico.dto;

/**
 * @param novoToken token novo para quem pediu o encerramento, quando a própria
 *                  conta foi atingida (assim a sessão atual não cai); senão null.
 */
public record EncerrarSessoesResponse(String novoToken) {
}
