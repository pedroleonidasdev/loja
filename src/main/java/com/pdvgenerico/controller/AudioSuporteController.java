package com.pdvgenerico.controller;

import com.pdvgenerico.dto.MensagemSuporteResponse;
import com.pdvgenerico.model.AudioPrivado;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.AudioSuporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/** Mensagens de voz do chat privado. O corpo do POST é o próprio arquivo de áudio. */
@RestController
@RequestMapping("/api/suporte")
@RequiredArgsConstructor
public class AudioSuporteController {

    private final AudioSuporteService audioService;

    @PostMapping("/conversas/{outroId}/audios")
    public MensagemSuporteResponse enviar(@AuthenticationPrincipal Usuario eu,
                                          @PathVariable Long outroId,
                                          @RequestParam(defaultValue = "0") int duracao,
                                          @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                          @RequestBody byte[] dados) {
        return MensagemSuporteResponse.fromEntity(audioService.enviar(eu, outroId, dados, contentType, duracao));
    }

    @GetMapping("/audios/{mensagemId}")
    public ResponseEntity<byte[]> baixar(@AuthenticationPrincipal Usuario eu, @PathVariable Long mensagemId) {
        AudioPrivado audio = audioService.obter(eu, mensagemId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.getTipoMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(audio.getDados());
    }
}
