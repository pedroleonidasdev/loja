package com.pdvgenerico.controller;

import com.pdvgenerico.dto.CaixaRequest;
import com.pdvgenerico.dto.CaixaResponse;
import com.pdvgenerico.model.Caixa;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.CaixaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/caixa")
@RequiredArgsConstructor
public class CaixaController {

    private final CaixaService caixaService;

    @GetMapping("/atual")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAIXA')")
    public ResponseEntity<CaixaResponse> caixaAtual() {
        return caixaService.buscarCaixaAberto()
                .map(caixa -> ResponseEntity.ok(CaixaResponse.fromEntity(caixa)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<CaixaResponse> listarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        List<Caixa> caixas = caixaService.listarPorPeriodo(inicio, fim);
        return caixas.stream().map(CaixaResponse::fromEntity).toList();
    }

    @PostMapping("/abrir")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAIXA')")
    public ResponseEntity<CaixaResponse> abrir(@Valid @RequestBody CaixaRequest request,
                                                @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(CaixaResponse.fromEntity(caixaService.abrir(request, usuarioLogado)));
    }

    // Só ADMIN pode fechar o caixa — decisão de negócio: o fechamento do dia é conferido
    // e confirmado pelo administrador, mesmo que qualquer operador possa abrir pela manhã.
    @PostMapping("/fechar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CaixaResponse> fechar(@Valid @RequestBody CaixaRequest.FechamentoRequest request,
                                                 @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(CaixaResponse.fromEntity(caixaService.fechar(request, usuarioLogado)));
    }
}
