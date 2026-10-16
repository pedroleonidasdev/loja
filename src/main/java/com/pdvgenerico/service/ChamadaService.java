package com.pdvgenerico.service;

import com.pdvgenerico.dto.ChamadaResponse;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinalização das chamadas de áudio/vídeo (WebRTC) entre dois usuários.
 *
 * O áudio e o vídeo trafegam direto entre os navegadores; o servidor só repassa
 * as descrições de sessão (oferta e resposta) para os dois lados se encontrarem.
 * Esse estado é de curtíssima duração, por isso fica em memória e não no banco —
 * só o resultado final (feita, perdida, recusada...) vira mensagem no histórico
 * da conversa. Consequência: com mais de uma instância do backend (ou se ele
 * reiniciar no meio de uma chamada), a chamada em curso cai.
 *
 * Sem WebSocket, o frontend consulta o estado por polling. Cada consulta conta
 * como "sinal de vida": se um dos lados some (fechou a aba, caiu a rede), a
 * chamada é encerrada sozinha.
 */
@Service
@RequiredArgsConstructor
public class ChamadaService {

    private static final Logger log = LoggerFactory.getLogger(ChamadaService.class);

    public enum Status { TOCANDO, EM_ANDAMENTO, ENCERRADA, RECUSADA, NAO_ATENDIDA, CANCELADA }

    private static final Duration TEMPO_TOCANDO = Duration.ofSeconds(45);
    private static final Duration SEM_SINAL_TOCANDO = Duration.ofSeconds(12);
    private static final Duration SEM_SINAL_EM_ANDAMENTO = Duration.ofSeconds(25);
    private static final Duration GUARDAR_ENCERRADAS = Duration.ofMinutes(2);

    private final UsuarioRepository usuarioRepository;
    private final SuporteService suporteService;

    private final Map<String, Chamada> chamadas = new ConcurrentHashMap<>();

    private static final class Chamada {
        final String id = UUID.randomUUID().toString();
        Long chamadorId;
        String chamadorNome;
        Long chamadoId;
        String chamadoNome;
        String tipo;
        Status status = Status.TOCANDO;
        String oferta;
        String resposta;
        Instant criadaEm = Instant.now();
        Instant atendidaEm;
        Instant encerradaEm;
        Instant sinalChamador = Instant.now();
        Instant sinalChamado = Instant.now();

        boolean ativa() {
            return status == Status.TOCANDO || status == Status.EM_ANDAMENTO;
        }

        boolean participa(Long usuarioId) {
            return usuarioId.equals(chamadorId) || usuarioId.equals(chamadoId);
        }
    }

    public synchronized ChamadaResponse iniciar(Usuario eu, Long destinatarioId, String tipo, String oferta) {
        String tipoNormalizado = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!tipoNormalizado.equals("AUDIO") && !tipoNormalizado.equals("VIDEO")) {
            throw new BusinessException("Tipo de chamada inválido");
        }
        if (destinatarioId == null || destinatarioId.equals(eu.getId())) {
            throw new BusinessException("Escolha outra pessoa para ligar");
        }
        Usuario outro = usuarioRepository.findById(destinatarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id " + destinatarioId));
        if (!outro.isAtivo()) {
            throw new BusinessException("Esse usuário está inativo");
        }

        atualizarTodas();
        if (chamadaAtivaDe(eu.getId()).isPresent()) {
            throw new BusinessException("Você já está em uma chamada");
        }
        if (chamadaAtivaDe(destinatarioId).isPresent()) {
            throw new BusinessException(outro.getNome() + " está ocupado(a) em outra chamada");
        }

        Chamada c = new Chamada();
        c.chamadorId = eu.getId();
        c.chamadorNome = eu.getNome();
        c.chamadoId = outro.getId();
        c.chamadoNome = outro.getNome();
        c.tipo = tipoNormalizado;
        c.oferta = oferta;
        chamadas.put(c.id, c);
        return paraResposta(c, eu.getId());
    }

    /** Chamada ativa (tocando ou em andamento) de que o usuário participa, se houver. */
    public synchronized Optional<ChamadaResponse> atual(Usuario eu) {
        atualizarTodas();
        return chamadaAtivaDe(eu.getId()).map(c -> paraResposta(c, eu.getId()));
    }

    /** Consulta do estado; também serve de sinal de vida do solicitante. */
    public synchronized ChamadaResponse obter(Usuario eu, String id) {
        Chamada c = buscar(eu, id);
        if (c.ativa()) {
            if (eu.getId().equals(c.chamadorId)) {
                c.sinalChamador = Instant.now();
            } else {
                c.sinalChamado = Instant.now();
            }
        }
        atualizar(c);
        return paraResposta(c, eu.getId());
    }

    public synchronized ChamadaResponse atender(Usuario eu, String id, String resposta) {
        Chamada c = buscar(eu, id);
        atualizar(c);
        if (!eu.getId().equals(c.chamadoId)) {
            throw new BusinessException("Só quem recebe a chamada pode atendê-la");
        }
        if (c.status != Status.TOCANDO) {
            throw new BusinessException("Essa chamada não está mais tocando");
        }
        c.resposta = resposta;
        c.status = Status.EM_ANDAMENTO;
        c.atendidaEm = Instant.now();
        c.sinalChamador = Instant.now();
        c.sinalChamado = Instant.now();
        return paraResposta(c, eu.getId());
    }

    public synchronized ChamadaResponse recusar(Usuario eu, String id) {
        Chamada c = buscar(eu, id);
        atualizar(c);
        if (!eu.getId().equals(c.chamadoId)) {
            throw new BusinessException("Só quem recebe a chamada pode recusá-la");
        }
        if (c.status == Status.TOCANDO) {
            finalizar(c, Status.RECUSADA);
        }
        return paraResposta(c, eu.getId());
    }

    /** Desliga: cancela se ainda tocava para o outro lado, encerra se estava em andamento. */
    public synchronized ChamadaResponse encerrar(Usuario eu, String id) {
        Chamada c = buscar(eu, id);
        atualizar(c);
        if (c.status == Status.TOCANDO) {
            finalizar(c, eu.getId().equals(c.chamadorId) ? Status.CANCELADA : Status.RECUSADA);
        } else if (c.status == Status.EM_ANDAMENTO) {
            finalizar(c, Status.ENCERRADA);
        }
        return paraResposta(c, eu.getId());
    }

    // ---- internos (sempre chamados com o lock do serviço) ----

    private Chamada buscar(Usuario eu, String id) {
        Chamada c = id == null ? null : chamadas.get(id);
        // quem não participa recebe o mesmo "não encontrada": não revela que a chamada existe
        if (c == null || !c.participa(eu.getId())) {
            throw new ResourceNotFoundException("Chamada não encontrada");
        }
        return c;
    }

    private Optional<Chamada> chamadaAtivaDe(Long usuarioId) {
        return chamadas.values().stream()
                .filter(c -> c.ativa() && c.participa(usuarioId))
                .findFirst();
    }

    private void atualizarTodas() {
        Instant agora = Instant.now();
        for (Chamada c : chamadas.values()) {
            atualizar(c);
            if (!c.ativa() && c.encerradaEm != null
                    && Duration.between(c.encerradaEm, agora).compareTo(GUARDAR_ENCERRADAS) > 0) {
                chamadas.remove(c.id);
            }
        }
    }

    /** Aplica os prazos: ninguém atendeu a tempo, ou um dos lados parou de dar sinal de vida. */
    private void atualizar(Chamada c) {
        Instant agora = Instant.now();
        if (c.status == Status.TOCANDO) {
            if (Duration.between(c.criadaEm, agora).compareTo(TEMPO_TOCANDO) > 0) {
                finalizar(c, Status.NAO_ATENDIDA);
            } else if (Duration.between(c.sinalChamador, agora).compareTo(SEM_SINAL_TOCANDO) > 0) {
                finalizar(c, Status.CANCELADA);
            }
        } else if (c.status == Status.EM_ANDAMENTO) {
            if (Duration.between(c.sinalChamador, agora).compareTo(SEM_SINAL_EM_ANDAMENTO) > 0
                    || Duration.between(c.sinalChamado, agora).compareTo(SEM_SINAL_EM_ANDAMENTO) > 0) {
                finalizar(c, Status.ENCERRADA);
            }
        }
    }

    private void finalizar(Chamada c, Status novoStatus) {
        if (!c.ativa()) {
            return;
        }
        c.status = novoStatus;
        c.encerradaEm = Instant.now();
        // a oferta/resposta não são mais necessárias: não deixa SDP parado em memória
        c.oferta = null;
        c.resposta = null;

        String nome = c.tipo.equals("VIDEO") ? "Chamada de vídeo" : "Chamada de áudio";
        String texto;
        boolean naoLida = false;
        switch (novoStatus) {
            case ENCERRADA -> texto = nome + " · " + duracao(c);
            case RECUSADA -> texto = nome + " recusada";
            case CANCELADA -> {
                texto = nome + " cancelada";
                naoLida = true;
            }
            default -> {
                texto = nome + " não atendida";
                naoLida = true;
            }
        }
        try {
            suporteService.registrarChamada(c.chamadorId, c.chamadoId, texto, naoLida);
        } catch (RuntimeException e) {
            // falha ao gravar o histórico não pode impedir o encerramento da chamada
            log.warn("Não foi possível registrar a chamada no histórico: {}", e.getMessage());
        }
    }

    private String duracao(Chamada c) {
        if (c.atendidaEm == null) {
            return "0:00";
        }
        long total = Math.max(0, Duration.between(c.atendidaEm, c.encerradaEm).getSeconds());
        long horas = total / 3600;
        long minutos = (total % 3600) / 60;
        long segundos = total % 60;
        return horas > 0
                ? String.format("%d:%02d:%02d", horas, minutos, segundos)
                : String.format("%d:%02d", minutos, segundos);
    }

    private ChamadaResponse paraResposta(Chamada c, Long euId) {
        boolean souChamador = euId.equals(c.chamadorId);
        return new ChamadaResponse(
                c.id,
                c.tipo,
                c.status.name(),
                souChamador ? "SAIDA" : "ENTRADA",
                souChamador ? c.chamadoId : c.chamadorId,
                souChamador ? c.chamadoNome : c.chamadorNome,
                souChamador ? null : c.oferta,
                souChamador ? c.resposta : null,
                c.criadaEm,
                c.atendidaEm);
    }
}
