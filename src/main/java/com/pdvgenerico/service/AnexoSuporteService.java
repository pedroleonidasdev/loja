package com.pdvgenerico.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.AnexoPrivado;
import com.pdvgenerico.model.MensagemSuporte;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.AnexoPrivadoRepository;
import com.pdvgenerico.repository.MensagemSuporteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arquivos anexados no chat privado, de QUALQUER formato. Os bytes ficam no banco, que costuma
 * ter pouco espaço no plano gratuito; por isso há três limites: tamanho por arquivo, espaço total
 * e validade (o arquivo expira depois de alguns dias, mesmo que a mensagem continue no histórico).
 *
 * Segurança: o servidor nunca confia no tipo informado pelo navegador. Só imagens PNG/JPEG/GIF/WebP
 * (confirmadas pelos primeiros bytes) são tratadas como imagem; todo o resto vira
 * application/octet-stream e é sempre entregue como download.
 */
@Service
public class AnexoSuporteService {

    private static final int MAX_NOME = 120;
    private static final Duration RETENCAO_ANEXO = Duration.ofDays(7);
    public static final String TIPO_GENERICO = "application/octet-stream";

    private final SuporteService suporteService;
    private final AnexoPrivadoRepository anexoRepository;
    private final MensagemSuporteRepository mensagemRepository;
    private final ObjectMapper objectMapper;
    private final long maxArquivoBytes;
    private final long maxTotalBytes;

    public AnexoSuporteService(SuporteService suporteService,
                               AnexoPrivadoRepository anexoRepository,
                               MensagemSuporteRepository mensagemRepository,
                               ObjectMapper objectMapper,
                               @Value("${app.suporte.anexos.max-mb:5}") long maxArquivoMb,
                               @Value("${app.suporte.anexos.max-total-mb:100}") long maxTotalMb) {
        this.suporteService = suporteService;
        this.anexoRepository = anexoRepository;
        this.mensagemRepository = mensagemRepository;
        this.objectMapper = objectMapper;
        this.maxArquivoBytes = maxArquivoMb * 1024 * 1024;
        this.maxTotalBytes = maxTotalMb * 1024 * 1024;
    }

    @Transactional
    public MensagemSuporte enviar(Usuario eu, Long outroId, MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BusinessException("O arquivo está vazio");
        }
        if (arquivo.getSize() > maxArquivoBytes) {
            throw new BusinessException("O arquivo é grande demais (máximo " + (maxArquivoBytes / 1024 / 1024) + " MB)");
        }
        byte[] dados;
        try {
            dados = arquivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler o arquivo enviado");
        }

        // libera o espaço dos anexos vencidos antes de conferir o limite total
        limparAnexos();
        if (anexoRepository.somaTamanhos() + dados.length > maxTotalBytes) {
            throw new BusinessException("O espaço para anexos do chat está cheio. Os arquivos antigos expiram sozinhos em "
                    + RETENCAO_ANEXO.toDays() + " dias; tente novamente mais tarde ou apague conversas antigas.");
        }

        String mime = detectarMime(dados);
        String nome = sanitizarNome(arquivo.getOriginalFilename());

        // o texto da mensagem guarda os dados de exibição: nome, tamanho e tipo
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("n", nome);
        meta.put("t", dados.length);
        meta.put("m", mime);
        String texto;
        try {
            texto = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Não foi possível registrar o anexo");
        }

        MensagemSuporte mensagem = suporteService.registrarAnexo(eu, outroId, texto);
        anexoRepository.save(AnexoPrivado.builder()
                .mensagemId(mensagem.getId())
                .tipoMime(mime)
                .tamanho((long) dados.length)
                .dados(dados)
                .criadaEm(Instant.now())
                .build());
        return mensagem;
    }

    /** Só quem enviou ou recebeu a mensagem (e ainda não apagou a conversa) consegue o arquivo. */
    @Transactional(readOnly = true)
    public AnexoPrivado obter(Usuario eu, Long mensagemId) {
        MensagemSuporte m = mensagemRepository.findById(mensagemId)
                .orElseThrow(() -> new ResourceNotFoundException("Anexo não encontrado"));
        boolean souRemetente = eu.getId().equals(m.getRemetenteId()) && !m.isApagadaRemetente();
        boolean souDestinatario = eu.getId().equals(m.getDestinatarioId()) && !m.isApagadaDestinatario();
        if (m.isApagadaParaTodos() || !SuporteService.TIPO_ARQUIVO.equals(m.getTipo()) || !(souRemetente || souDestinatario)) {
            throw new ResourceNotFoundException("Anexo não encontrado");
        }
        AnexoPrivado anexo = anexoRepository.findByMensagemId(mensagemId)
                .orElseThrow(() -> new ResourceNotFoundException("O arquivo não está mais disponível"));
        if (anexo.getCriadaEm().isBefore(Instant.now().minus(RETENCAO_ANEXO))) {
            throw new ResourceNotFoundException("O arquivo não está mais disponível");
        }
        return anexo;
    }

    /** Remove anexos vencidos e os que perderam a mensagem (conversa apagada, retenção do histórico). */
    @Transactional
    public void limparAnexos() {
        anexoRepository.excluirAnterioresA(Instant.now().minus(RETENCAO_ANEXO));
        anexoRepository.excluirSemMensagem();
    }

    /** Tira caminho, caracteres de controle e de direção de texto (usados para disfarçar extensões). */
    static String sanitizarNome(String original) {
        String nome = original == null ? "" : original;
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        nome = nome.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        if (nome.isEmpty() || nome.equals(".") || nome.equals("..")) {
            return "arquivo";
        }
        if (nome.length() > MAX_NOME) {
            int ponto = nome.lastIndexOf('.');
            String ext = ponto > 0 && nome.length() - ponto <= 11 ? nome.substring(ponto) : "";
            nome = nome.substring(0, MAX_NOME - ext.length()) + ext;
        }
        return nome;
    }

    /** Imagem só se os primeiros bytes confirmarem; qualquer outra coisa é um arquivo genérico. */
    static String detectarMime(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') {
            return "image/gif";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return TIPO_GENERICO;
    }
}
