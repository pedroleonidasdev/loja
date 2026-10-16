package com.pdvgenerico.service;

import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.AudioPrivado;
import com.pdvgenerico.model.MensagemSuporte;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.AudioPrivadoRepository;
import com.pdvgenerico.repository.MensagemSuporteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Mensagens de voz do chat privado. Os bytes ficam no banco, que costuma ter
 * pouco espaço no plano gratuito; por isso o áudio é pequeno (limites abaixo) e
 * expira depois de alguns dias, mesmo que a mensagem continue no histórico.
 */
@Service
@RequiredArgsConstructor
public class AudioSuporteService {

    public static final int MAX_BYTES = 600 * 1024;
    public static final int MAX_SEGUNDOS = 120;
    private static final Duration RETENCAO_AUDIO = Duration.ofDays(7);
    private static final Set<String> TIPOS_ACEITOS = Set.of("audio/mp4", "audio/webm", "audio/ogg");

    private final SuporteService suporteService;
    private final AudioPrivadoRepository audioRepository;
    private final MensagemSuporteRepository mensagemRepository;

    @Transactional
    public MensagemSuporte enviar(Usuario eu, Long outroId, byte[] dados, String contentType, int duracao) {
        if (dados == null || dados.length == 0) {
            throw new BusinessException("O áudio está vazio");
        }
        if (dados.length > MAX_BYTES) {
            throw new BusinessException("O áudio é grande demais");
        }
        if (duracao < 1 || duracao > MAX_SEGUNDOS) {
            throw new BusinessException("O áudio deve ter entre 1 segundo e 2 minutos");
        }
        String mime = normalizarMime(contentType);
        if (mime == null || !assinaturaConfere(dados, mime)) {
            throw new BusinessException("Formato de áudio não suportado");
        }

        MensagemSuporte mensagem = suporteService.registrarAudio(eu, outroId, duracao);
        audioRepository.save(AudioPrivado.builder()
                .mensagemId(mensagem.getId())
                .tipoMime(mime)
                .dados(dados)
                .criadaEm(Instant.now())
                .build());

        limparAudios();
        return mensagem;
    }

    /** Só quem enviou ou recebeu a mensagem (e ainda não apagou a conversa) consegue o áudio. */
    @Transactional(readOnly = true)
    public AudioPrivado obter(Usuario eu, Long mensagemId) {
        MensagemSuporte m = mensagemRepository.findById(mensagemId)
                .orElseThrow(() -> new ResourceNotFoundException("Áudio não encontrado"));
        boolean souRemetente = eu.getId().equals(m.getRemetenteId()) && !m.isApagadaRemetente();
        boolean souDestinatario = eu.getId().equals(m.getDestinatarioId()) && !m.isApagadaDestinatario();
        if (!SuporteService.TIPO_AUDIO.equals(m.getTipo()) || !(souRemetente || souDestinatario)) {
            throw new ResourceNotFoundException("Áudio não encontrado");
        }
        return audioRepository.findByMensagemId(mensagemId)
                .orElseThrow(() -> new ResourceNotFoundException("Áudio não está mais disponível"));
    }

    /** Remove áudios vencidos e os que perderam a mensagem (conversa apagada, retenção do histórico). */
    @Transactional
    public void limparAudios() {
        audioRepository.excluirAnterioresA(Instant.now().minus(RETENCAO_AUDIO));
        audioRepository.excluirSemMensagem();
    }

    /** Aceita só formatos de áudio conhecidos (ignora o ";codecs=..."). */
    private static String normalizarMime(String contentType) {
        if (contentType == null) {
            return null;
        }
        String base = contentType.split(";")[0].trim().toLowerCase();
        if (base.equals("video/webm")) {
            base = "audio/webm";
        }
        return TIPOS_ACEITOS.contains(base) ? base : null;
    }

    /** Confere os primeiros bytes: impede subir qualquer arquivo só dizendo que é "audio/...". */
    private static boolean assinaturaConfere(byte[] d, String mime) {
        if (d.length < 12) {
            return false;
        }
        return switch (mime) {
            case "audio/webm" -> (d[0] & 0xFF) == 0x1A && (d[1] & 0xFF) == 0x45 && (d[2] & 0xFF) == 0xDF && (d[3] & 0xFF) == 0xA3;
            case "audio/ogg" -> d[0] == 'O' && d[1] == 'g' && d[2] == 'g' && d[3] == 'S';
            case "audio/mp4" -> d[4] == 'f' && d[5] == 't' && d[6] == 'y' && d[7] == 'p';
            default -> false;
        };
    }
}
