package com.pdvgenerico.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Versão do servidor em execução, sem autenticação e sem tocar no banco. O frontend consulta
 * isto de tempos em tempos para avisar quando o servidor está atualizando/reiniciando e quando
 * ele volta numa versão nova (ver AtualizacaoServidor no frontend).
 *
 * <ul>
 *   <li>{@code versao}: hash do commit publicado (o Render informa em RENDER_GIT_COMMIT); vazio se
 *       o ambiente não informar.</li>
 *   <li>{@code iniciadoEm}: quando este processo subiu. Muda a cada reinício/deploy, mesmo que a
 *       versão seja a mesma.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/versao")
public class VersaoController {

    public record VersaoResponse(String versao, Instant iniciadoEm) {
    }

    private final String versao;
    private final Instant iniciadoEm = Instant.now();

    public VersaoController(@Value("${app.versao:}") String versao) {
        this.versao = versao == null ? "" : versao.trim();
    }

    @GetMapping
    public ResponseEntity<VersaoResponse> versao() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new VersaoResponse(versao, iniciadoEm));
    }
}
