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

    public static final String TIPO_CHAMADA = "CHAMADA";
    public static final String TIPO_FIGURINHA = "FIGURINHA";
    public static final String TIPO_AUDIO = "AUDIO";
    public static final String TIPO_ARQUIVO = "ARQUIVO";
    private static final int MAX_FIGURINHA = 80;

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
        return enviar(eu, outroId, textoOriginal, null);
    }

    /** {@code tipo}: nulo para texto comum ou "FIGURINHA". "CHAMADA" é só do sistema e não é aceito aqui. */
    @Transactional
    public MensagemSuporte enviar(Usuario eu, Long outroId, String textoOriginal, String tipoOriginal) {
        validarContato(eu, outroId);
        String texto = textoOriginal == null ? "" : textoOriginal.trim();
        if (texto.isEmpty()) {
            throw new BusinessException("A mensagem não pode estar vazia");
        }
        String tipo = tipoOriginal == null || tipoOriginal.isBlank() ? null : tipoOriginal.trim().toUpperCase();
        if (tipo != null) {
            if (!tipo.equals(TIPO_FIGURINHA)) {
                throw new BusinessException("Tipo de mensagem inválido");
            }
            if (texto.length() > MAX_FIGURINHA) {
                throw new BusinessException("Figurinha inválida");
            }
        }

        MensagemSuporte salva = repository.save(MensagemSuporte.builder()
                .remetenteId(eu.getId())
                .destinatarioId(outroId)
                .texto(texto)
                .tipo(tipo)
                .criadaEm(Instant.now())
                .build());

        // limpeza ocasional do histórico antigo, para a tabela não crescer sem fim
        if (salva.getId() % 50 == 0) {
            repository.deleteByCriadaEmBefore(Instant.now().minus(RETENCAO));
        }
        return salva;
    }

    /**
     * Cria a mensagem de voz (o texto guarda só a duração em segundos). Os bytes do
     * áudio são salvos à parte por {@link AudioSuporteService}.
     */
    @Transactional
    public MensagemSuporte registrarAudio(Usuario eu, Long outroId, int duracaoSegundos) {
        validarContato(eu, outroId);
        return repository.save(MensagemSuporte.builder()
                .remetenteId(eu.getId())
                .destinatarioId(outroId)
                .texto(String.valueOf(duracaoSegundos))
                .tipo(TIPO_AUDIO)
                .criadaEm(Instant.now())
                .build());
    }

    /**
     * Cria a mensagem de um arquivo anexado (o texto guarda nome, tamanho e tipo em JSON). Os bytes
     * são salvos à parte por {@link AnexoSuporteService}.
     */
    @Transactional
    public MensagemSuporte registrarAnexo(Usuario eu, Long outroId, String textoMeta) {
        validarContato(eu, outroId);
        return repository.save(MensagemSuporte.builder()
                .remetenteId(eu.getId())
                .destinatarioId(outroId)
                .texto(textoMeta)
                .tipo(TIPO_ARQUIVO)
                .criadaEm(Instant.now())
                .build());
    }

    /** Marca como lidas as mensagens que o outro enviou para mim. */
    @Transactional
    public void marcarComoLidas(Usuario eu, Long outroId) {
        validarContato(eu, outroId);
        repository.marcarComoLidas(eu.getId(), outroId);
    }

    /**
     * "Apagar conversa": some da tela de quem apagou, mas continua visível para a
     * outra pessoa. Quando os dois apagam, as mensagens são excluídas do banco.
     * Não exige que o outro usuário esteja ativo — dá para limpar o histórico
     * de alguém que foi desativado.
     */
    @Transactional
    public void apagarConversa(Usuario eu, Long outroId) {
        if (outroId == null || outroId.equals(eu.getId())) {
            throw new BusinessException("Escolha outra pessoa");
        }
        if (!usuarioRepository.existsById(outroId)) {
            throw new ResourceNotFoundException("Usuário não encontrado: id " + outroId);
        }
        repository.apagarEnviadasPor(eu.getId(), outroId);
        repository.apagarRecebidasPor(eu.getId(), outroId);
        repository.excluirApagadasPelosDois(eu.getId(), outroId);
    }

    /**
     * Registra no histórico da conversa o resultado de uma chamada (ex.: "Chamada de
     * áudio · 02:13" ou "Chamada de vídeo perdida"). Perdidas contam como não lidas.
     */
    @Transactional
    public void registrarChamada(Long remetenteId, Long destinatarioId, String texto, boolean naoLida) {
        repository.save(MensagemSuporte.builder()
                .remetenteId(remetenteId)
                .destinatarioId(destinatarioId)
                .texto(texto)
                .tipo(TIPO_CHAMADA)
                .criadaEm(Instant.now())
                .lida(!naoLida)
                .build());
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
