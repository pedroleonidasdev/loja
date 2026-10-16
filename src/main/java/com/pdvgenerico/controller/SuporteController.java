package com.pdvgenerico.controller;

import com.pdvgenerico.dto.ContatoSuporteResponse;
import com.pdvgenerico.dto.MensagemSuporteRequest;
import com.pdvgenerico.dto.MensagemSuporteResponse;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.SuporteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Chat privado entre usuários logados (ADMIN e CAIXA). Cada conversa é só de dois. */
@RestController
@RequestMapping("/api/suporte")
@RequiredArgsConstructor
public class SuporteController {

    private final SuporteService suporteService;

    @GetMapping("/contatos")
    public List<ContatoSuporteResponse> contatos(@AuthenticationPrincipal Usuario eu) {
        return suporteService.contatos(eu);
    }

    @GetMapping("/conversas/{outroId}/mensagens")
    public List<MensagemSuporteResponse> listar(@AuthenticationPrincipal Usuario eu,
                                                @PathVariable Long outroId,
                                                @RequestParam(required = false) Long depois) {
        return suporteService.listar(eu, outroId, depois).stream()
                .map(MensagemSuporteResponse::fromEntity)
                .toList();
    }

    @PostMapping("/conversas/{outroId}/mensagens")
    public MensagemSuporteResponse enviar(@AuthenticationPrincipal Usuario eu,
                                          @PathVariable Long outroId,
                                          @Valid @RequestBody MensagemSuporteRequest request) {
        return MensagemSuporteResponse.fromEntity(suporteService.enviar(eu, outroId, request.texto()));
    }

    @PostMapping("/conversas/{outroId}/lidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLidas(@AuthenticationPrincipal Usuario eu, @PathVariable Long outroId) {
        suporteService.marcarComoLidas(eu, outroId);
    }

    /** Apaga a conversa só para quem pediu; a outra pessoa continua vendo o histórico dela. */
    @DeleteMapping("/conversas/{outroId}/mensagens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarConversa(@AuthenticationPrincipal Usuario eu, @PathVariable Long outroId) {
        suporteService.apagarConversa(eu, outroId);
    }
}
