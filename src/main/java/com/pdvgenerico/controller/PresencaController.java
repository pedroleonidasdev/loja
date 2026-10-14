package com.pdvgenerico.controller;

import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.PresencaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presenca")
@RequiredArgsConstructor
public class PresencaController {

    private final PresencaService presencaService;

    /** Batimento periódico do frontend: o JwtAuthFilter já registra o acesso. */
    @PostMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sair")
    public ResponseEntity<Void> sair(@AuthenticationPrincipal Usuario usuario) {
        presencaService.registrarSaida(usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
