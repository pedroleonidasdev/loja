package com.pdvgenerico.service;

import com.pdvgenerico.dto.ContatoSuporteResponse;
import com.pdvgenerico.dto.MensagemSuporteResponse;
import com.pdvgenerico.dto.StatusConversaResponse;
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
import java.util.concurrent.ConcurrentHashMap;

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

    /** Por quanto tempo um aviso de "digitando" continua valendo sem ser renovado. */
    private static final Duration DIGITANDO_VALIDADE = Duration.ofSeconds(5);
    private static final Duration JANELA_EDICAO = Duration.ofMinutes(15);
    private static final Duration JANELA_APAGAR_PARA_TODOS = Duration.ofHours(48);
    private static final int MAX_PREVIA = 60;

    private final MensagemSuporteRepository repository;
    private final UsuarioRepository usuarioRepository;

    /** "quem>para" -> instante do último aviso de digitação. Fica só em memória (dura segundos). */
    private final Map<String, Instant> digitando = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<ContatoSuporteResponse> contatos(Usuario eu) {
        Map<Long, Long> naoLidas = new HashMap<>();
        for (Object[] linha : repository.contarNaoLidasPorRemetente(eu.getId())) {
            naoLidas.put((Long) linha[0], (Long) linha[1]);
        }
        return usuarioRepository.findAll().stream()
                .filter(u -> u.isAtivo() && !u.getId().equals(eu.getId()))
                .map(u -> {
                    UsuarioResponse presenca = UsuarioResponse.fromEntity(u);
                    List<MensagemSuporte> ultimas = repository.recentes(eu.getId(), u.getId(), PageRequest.of(0, 1));
                    MensagemSuporte ultima = ultimas == null || ultimas.isEmpty() ? null : ultimas.get(0);
                    String previa = ultima == null ? null : previaDe(ultima, eu.getId());
                    return new ContatoSuporteResponse(
                            u.getId(),
                            u.getNome(),
                            u.getLogin(),
                            u.getPerfil().name(),
                            presenca.online(),
                            naoLidas.getOrDefault(u.getId(), 0L),
                            estaDigitando(u.getId(), eu.getId()),
                            previa,
                            ultima == null ? null : ultima.getCriadaEm(),
                            presenca.ultimoAcessoHaSeg());
                })
                // quem tem conversa mais recente vem primeiro; quem nunca conversou fica por ordem alfabética
                .sorted(Comparator
                        .comparing((ContatoSuporteResponse c) -> c.ultimaEm() == null ? Instant.EPOCH : c.ultimaEm())
                        .reversed()
                        .thenComparing(ContatoSuporteResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Texto curto da última mensagem para a lista de conversas ("Você: ..." quando fui eu que enviei). */
    static String previaDe(MensagemSuporte m, Long euId) {
        String corpo = previaCorpo(m);
        return euId != null && euId.equals(m.getRemetenteId()) && !TIPO_CHAMADA.equals(m.getTipo())
                ? "Você: " + corpo
                : corpo;
    }

    /** Prévia sem o prefixo "Você:" (também usada na citação de uma resposta). */
    static String previaCorpo(MensagemSuporte m) {
        if (m.isApagadaParaTodos()) {
            return "🚫 Mensagem apagada";
        }
        String tipo = m.getTipo();
        if (TIPO_AUDIO.equals(tipo)) return "🎤 Mensagem de voz";
        if (TIPO_ARQUIVO.equals(tipo)) return "📎 Arquivo";
        if (TIPO_CHAMADA.equals(tipo)) return "📞 " + m.getTexto();
        String texto = m.getTexto() == null ? "" : m.getTexto().trim().replaceAll("\\s+", " ");
        return texto.length() > MAX_PREVIA ? texto.substring(0, MAX_PREVIA - 1) + "…" : texto;
    }

    // ---------- digitando ----------

    /** O usuário logado está digitando para {@code outroId}; vale por alguns segundos. */
    public void avisarDigitando(Usuario eu, Long outroId) {
        validarContato(eu, outroId);
        digitando.put(eu.getId() + ">" + outroId, Instant.now());
    }

    private boolean estaDigitando(Long quemId, Long paraId) {
        Instant quando = digitando.get(quemId + ">" + paraId);
        return quando != null && quando.isAfter(Instant.now().minus(DIGITANDO_VALIDADE));
    }

    /** Estado do outro lado da conversa: digitando, online e há quanto tempo foi visto. */
    @Transactional(readOnly = true)
    public StatusConversaResponse status(Usuario eu, Long outroId) {
        Usuario outro = validarContato(eu, outroId);
        UsuarioResponse presenca = UsuarioResponse.fromEntity(outro);
        return new StatusConversaResponse(estaDigitando(outroId, eu.getId()), presenca.online(), presenca.ultimoAcessoHaSeg());
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
        return enviar(eu, outroId, textoOriginal, tipoOriginal, null);
    }

    /** {@code respostaAId}: id de uma mensagem desta mesma conversa que está sendo respondida (ou nulo). */
    @Transactional
    public MensagemSuporte enviar(Usuario eu, Long outroId, String textoOriginal, String tipoOriginal, Long respostaAId) {
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

        if (respostaAId != null) {
            MensagemSuporte citada = repository.findById(respostaAId).orElse(null);
            boolean daConversa = citada != null
                    && ((citada.getRemetenteId().equals(eu.getId()) && citada.getDestinatarioId().equals(outroId))
                    || (citada.getRemetenteId().equals(outroId) && citada.getDestinatarioId().equals(eu.getId())));
            if (!daConversa) {
                throw new BusinessException("A mensagem respondida não foi encontrada nesta conversa");
            }
        }

        MensagemSuporte salva = repository.save(MensagemSuporte.builder()
                .remetenteId(eu.getId())
                .destinatarioId(outroId)
                .texto(texto)
                .tipo(tipo)
                .respostaAId(respostaAId)
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

    private Usuario validarContato(Usuario eu, Long outroId) {
        if (outroId == null || outroId.equals(eu.getId())) {
            throw new BusinessException("Escolha outra pessoa para conversar");
        }
        Usuario outro = usuarioRepository.findById(outroId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id " + outroId));
        if (!outro.isAtivo()) {
            throw new BusinessException("Esse usuário está inativo");
        }
        return outro;
    }

    // ---------- respostas (citação) ----------

    /** Converte as mensagens para a resposta da API, já com o resumo da mensagem citada (se houver). */
    @Transactional(readOnly = true)
    public List<MensagemSuporteResponse> comRespostas(List<MensagemSuporte> mensagens) {
        List<Long> ids = mensagens.stream()
                .map(MensagemSuporte::getRespostaAId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, MensagemSuporte> citadas = new HashMap<>();
        if (!ids.isEmpty()) {
            for (MensagemSuporte c : repository.findAllById(ids)) {
                citadas.put(c.getId(), c);
            }
        }
        List<MensagemSuporteResponse> resposta = new ArrayList<>();
        for (MensagemSuporte m : mensagens) {
            MensagemSuporte citada = m.getRespostaAId() == null ? null : citadas.get(m.getRespostaAId());
            resposta.add(MensagemSuporteResponse.fromEntity(m, resumo(citada)));
        }
        return resposta;
    }

    @Transactional(readOnly = true)
    public MensagemSuporteResponse comResposta(MensagemSuporte mensagem) {
        return comRespostas(List.of(mensagem)).get(0);
    }

    private static MensagemSuporteResponse.Resposta resumo(MensagemSuporte citada) {
        if (citada == null) {
            return null;
        }
        return new MensagemSuporteResponse.Resposta(citada.getId(), citada.getRemetenteId(), previaCorpo(citada));
    }

    // ---------- ações sobre uma mensagem (reagir, editar, apagar) ----------

    /** Mensagem da qual eu participo e que não escondi para mim; senão, "não encontrada". */
    private MensagemSuporte minhaMensagem(Usuario eu, Long mensagemId) {
        MensagemSuporte m = repository.findById(mensagemId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensagem não encontrada"));
        boolean souRemetente = eu.getId().equals(m.getRemetenteId()) && !m.isApagadaRemetente();
        boolean souDestinatario = eu.getId().equals(m.getDestinatarioId()) && !m.isApagadaDestinatario();
        if (!souRemetente && !souDestinatario) {
            throw new ResourceNotFoundException("Mensagem não encontrada");
        }
        return m;
    }

    /** Reage com um emoji (uma reação por pessoa, como no WhatsApp). Emoji vazio remove a reação. */
    @Transactional
    public MensagemSuporte reagir(Usuario eu, Long mensagemId, String emoji) {
        MensagemSuporte m = minhaMensagem(eu, mensagemId);
        if (m.isApagadaParaTodos() || TIPO_CHAMADA.equals(m.getTipo())) {
            throw new BusinessException("Não dá para reagir a esta mensagem");
        }
        String valor = emoji == null ? "" : emoji.trim();
        if (valor.isEmpty()) {
            valor = null;
        } else if (valor.length() > 16 || valor.codePoints().anyMatch(Character::isLetterOrDigit)) {
            throw new BusinessException("Reação inválida");
        }
        if (eu.getId().equals(m.getRemetenteId())) {
            m.setReacaoRemetente(valor);
        } else {
            m.setReacaoDestinatario(valor);
        }
        return repository.save(m);
    }

    /** Edita o texto de uma mensagem minha, nos primeiros minutos. */
    @Transactional
    public MensagemSuporte editar(Usuario eu, Long mensagemId, String novoTexto) {
        MensagemSuporte m = minhaMensagem(eu, mensagemId);
        if (!eu.getId().equals(m.getRemetenteId())) {
            throw new BusinessException("Só dá para editar as suas próprias mensagens");
        }
        if (m.getTipo() != null || m.isApagadaParaTodos()) {
            throw new BusinessException("Só mensagens de texto podem ser editadas");
        }
        if (m.getCriadaEm().isBefore(Instant.now().minus(JANELA_EDICAO))) {
            throw new BusinessException("Só dá para editar nos primeiros 15 minutos");
        }
        String texto = novoTexto == null ? "" : novoTexto.trim();
        if (texto.isEmpty()) {
            throw new BusinessException("A mensagem não pode estar vazia");
        }
        if (texto.length() > 1000) {
            throw new BusinessException("A mensagem é grande demais");
        }
        if (texto.equals(m.getTexto())) {
            return m;
        }
        m.setTexto(texto);
        m.setEditada(true);
        return repository.save(m);
    }

    /**
     * Apaga uma mensagem. {@code paraTodos}: só quem enviou, em até 48 horas — o conteúdo some dos
     * dois lados e fica "Mensagem apagada". Sem isso, apaga só para mim (a outra pessoa continua vendo).
     */
    @Transactional
    public void apagarMensagem(Usuario eu, Long mensagemId, boolean paraTodos) {
        MensagemSuporte m = minhaMensagem(eu, mensagemId);
        boolean souRemetente = eu.getId().equals(m.getRemetenteId());
        if (paraTodos) {
            if (!souRemetente) {
                throw new BusinessException("Só dá para apagar para todos as mensagens que você enviou");
            }
            if (TIPO_CHAMADA.equals(m.getTipo())) {
                throw new BusinessException("Registros de chamada não podem ser apagados para todos");
            }
            if (m.getCriadaEm().isBefore(Instant.now().minus(JANELA_APAGAR_PARA_TODOS))) {
                throw new BusinessException("Só dá para apagar para todos em até 48 horas");
            }
            m.setApagadaParaTodos(true);
            m.setTexto("");
            m.setReacaoRemetente(null);
            m.setReacaoDestinatario(null);
            repository.save(m);
            return;
        }
        if (souRemetente) {
            m.setApagadaRemetente(true);
        } else {
            m.setApagadaDestinatario(true);
            m.setLida(true);
        }
        if (m.isApagadaRemetente() && m.isApagadaDestinatario()) {
            repository.delete(m);
        } else {
            repository.save(m);
        }
    }
}
