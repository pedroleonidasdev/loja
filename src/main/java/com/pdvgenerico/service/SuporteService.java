package com.pdvgenerico.service;

import com.pdvgenerico.dto.ContatoSuporteResponse;
import com.pdvgenerico.dto.UsuarioResponse;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.MensagemSuporte;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.MensagemSuporteRepository;
import com.pdvgenerico.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat privado 1 a 1. A privacidade vem de duas regras: toda consulta é
 * filtrada pelo usuário logado (o par "eu + outro") e o outro lado só é aceito
 * se for um usuário ativo e diferente de mim.
 */
@Service
@RequiredArgsConstructor
public class SuporteService {

    private static final Duration RETENCAO = Duration.ofDays(30);
    private static final int HISTORICO_INICIAL = 50;
    private static final int MAX_POR_BUSCA = 200;

    private final MensagemSuporteRepository repository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<ContatoSuporteResponse> contatos(Usuario eu) {
        Map<Long, Long> naoLidas = new HashMap<>();
        for (Object[] linha : repository.contarNaoLidasPorRemetente(eu.getId())) {
            naoLidas.put((Long) linha[0], (Long) linha[1]);
        }
        return usuarioRepository.findAll().stream()
                .filter(u -> u.isAtivo() && !u.getId().equals(eu.getId()))
                .sorted(Comparator.comparing(Usuario::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new ContatoSuporteResponse(
                        u.getId(),
                        u.getNome(),
                        u.getLogin(),
                        u.getPerfil().name(),
                        UsuarioResponse.fromEntity(u).online(),
                        naoLidas.getOrDefault(u.getId(), 0L)))
                .toList();
    }

    /**
     * Sem {@code depois}: as últimas 50 mensagens da conversa, em ordem cronológica.
     * Com {@code depois}: só as mensagens com id maior que ele.
     */
    @Transactional(readOnly = true)
    public List<MensagemSuporte> listar(Usuario eu, Long outroId, Long depois) {
        validarContato(eu, outroId);
        if (depois == null) {
            List<MensagemSuporte> recentes = new ArrayList<>(
                    repository.recentes(eu.getId(), outroId, PageRequest.of(0, HISTORICO_INICIAL)));
            Collections.reverse(recentes);
            return recentes;
        }
        return repository.depois(eu.getId(), outroId, depois, PageRequest.of(0, MAX_POR_BUSCA));
    }

    @Transactional
    public MensagemSuporte enviar(Usuario eu, Long outroId, String textoOriginal) {
        validarContato(eu, outroId);
        String texto = textoOriginal == null ? "" : textoOriginal.trim();
        if (texto.isEmpty()) {
            throw new BusinessException("A mensagem não pode estar vazia");
        }

        MensagemSuporte salva = repository.save(MensagemSuporte.builder()
                .remetenteId(eu.getId())
                .destinatarioId(outroId)
                .texto(texto)
                .criadaEm(Instant.now())
                .build());

        // limpeza ocasional do histórico antigo, para a tabela não crescer sem fim
        if (salva.getId() % 50 == 0) {
            repository.deleteByCriadaEmBefore(Instant.now().minus(RETENCAO));
        }
        return salva;
    }

    /** Marca como lidas as mensagens que o outro enviou para mim. */
    @Transactional
    public void marcarComoLidas(Usuario eu, Long outroId) {
        validarContato(eu, outroId);
        repository.marcarComoLidas(eu.getId(), outroId);
    }

    private void validarContato(Usuario eu, Long outroId) {
        if (outroId == null || outroId.equals(eu.getId())) {
            throw new BusinessException("Escolha outra pessoa para conversar");
        }
        Usuario outro = usuarioRepository.findById(outroId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id " + outroId));
        if (!outro.isAtivo()) {
            throw new BusinessException("Esse usuário está inativo");
        }
    }
}
