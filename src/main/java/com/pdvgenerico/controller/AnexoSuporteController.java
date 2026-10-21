package com.pdvgenerico.controller;

import com.pdvgenerico.dto.MensagemSuporteResponse;
import com.pdvgenerico.model.AnexoPrivado;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.AnexoSuporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/** Arquivos anexados no chat privado. O envio é multipart (campo "arquivo"). */
@RestController
@RequestMapping("/api/suporte")
@RequiredArgsConstructor
public class AnexoSuporteController {

    private final AnexoSuporteService anexoService;

    @PostMapping(value = "/conversas/{outroId}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MensagemSuporteResponse enviar(@AuthenticationPrincipal Usuario eu,
                                          @PathVariable Long outroId,
                                          @RequestParam("arquivo") MultipartFile arquivo) {
        return MensagemSuporteResponse.fromEntity(anexoService.enviar(eu, outroId, arquivo));
    }

    /** Sempre como download (attachment) e sem deixar o navegador "adivinhar" o tipo (nosniff). */
    @GetMapping("/anexos/{mensagemId}")
    public ResponseEntity<byte[]> baixar(@AuthenticationPrincipal Usuario eu, @PathVariable Long mensagemId) {
        AnexoPrivado anexo = anexoService.obter(eu, mensagemId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(anexo.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(anexo.getDados());
    }
}
