package com.pdvgenerico.service;

import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controla quem está online. Cada requisição autenticada (e o ping do frontend,
 * a cada ~60s) atualiza "ultimo_acesso"; quem acessou dentro da janela abaixo
 * é considerado online. A gravação é limitada a uma por usuário a cada
 * INTERVALO_GRAVACAO, para não escrever no banco a cada request.
 */
@Service
@RequiredArgsConstructor
public class PresencaService {

    /** Tempo sem atividade a partir do qual o usuário passa a aparecer como offline. */
    public static final Duration JANELA_ONLINE = Duration.ofMinutes(2);
    private static final Duration INTERVALO_GRAVACAO = Duration.ofSeconds(30);

    private final UsuarioRepository usuarioRepository;
    private final Map<Long, Long> ultimaGravacaoMs = new ConcurrentHashMap<>();

    @Transactional
    public void registrarAcesso(Long usuarioId) {
        long agora = System.currentTimeMillis();
        Long anterior = ultimaGravacaoMs.get(usuarioId);
        if (anterior != null && agora - anterior < INTERVALO_GRAVACAO.toMillis()) {
            return;
        }
        ultimaGravacaoMs.put(usuarioId, agora);
        usuarioRepository.atualizarUltimoAcesso(usuarioId, LocalDateTime.now());
    }

    /** Usuários ativos com atividade dentro da janela online (mesmo critério da tela de Usuários). */
    @Transactional(readOnly = true)
    public List<Usuario> listarOnline() {
        return usuarioRepository.findByAtivoTrueAndUltimoAcessoAfterOrderByNomeAsc(
                LocalDateTime.now().minus(JANELA_ONLINE));
    }

    /** Chamado no logout: marca o usuário como offline imediatamente. */
    @Transactional
    public void registrarSaida(Long usuarioId) {
        ultimaGravacaoMs.remove(usuarioId);
        usuarioRepository.atualizarUltimoAcesso(usuarioId, null);
    }
}
