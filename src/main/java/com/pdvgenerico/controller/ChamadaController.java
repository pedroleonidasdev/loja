package com.pdvgenerico.controller;

import com.pdvgenerico.dto.AtenderChamadaRequest;
import com.pdvgenerico.dto.ChamadaResponse;
import com.pdvgenerico.dto.IniciarChamadaRequest;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.ChamadaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Chamadas de áudio e vídeo 1 a 1 entre usuários logados (sinalização WebRTC). */
@RestController
@RequestMapping("/api/chamadas")
@RequiredArgsConstructor
public class ChamadaController {

    private final ChamadaService chamadaService;

    @PostMapping
    public ChamadaResponse iniciar(@AuthenticationPrincipal Usuario eu,
                                   @Valid @RequestBody IniciarChamadaRequest request) {
        return chamadaService.iniciar(eu, request.destinatarioId(), request.tipo(), request.oferta());
    }

    /** Chamada ativa do usuário (recebida tocando ou em andamento); 204 se não houver. */
    @GetMapping("/atual")
    public ResponseEntity<ChamadaResponse> atual(@AuthenticationPrincipal Usuario eu) {
        return chamadaService.atual(eu)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ChamadaResponse obter(@AuthenticationPrincipal Usuario eu, @PathVariable String id) {
        return chamadaService.obter(eu, id);
    }

    @PostMapping("/{id}/atender")
    public ChamadaResponse atender(@AuthenticationPrincipal Usuario eu,
                                   @PathVariable String id,
                                   @Valid @RequestBody AtenderChamadaRequest request) {
        return chamadaService.atender(eu, id, request.resposta());
    }

    @PostMapping("/{id}/recusar")
    public ChamadaResponse recusar(@AuthenticationPrincipal Usuario eu, @PathVariable String id) {
        return chamadaService.recusar(eu, id);
    }

    @PostMapping("/{id}/encerrar")
    public ChamadaResponse encerrar(@AuthenticationPrincipal Usuario eu, @PathVariable String id) {
        return chamadaService.encerrar(eu, id);
    }
}
