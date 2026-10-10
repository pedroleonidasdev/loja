package com.pdvgenerico.controller;

import com.pdvgenerico.dto.PushSubscriptionRequest;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.NotificacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    // Só ADMIN pode se inscrever: hoje só os admins recebem notificação
    // (venda / abertura / fechamento de caixa), então não faz sentido um
    // operador de caixa ativar e nunca receber nada.
    @PostMapping("/subscribe")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> inscrever(@Valid @RequestBody PushSubscriptionRequest request,
                                           @AuthenticationPrincipal Usuario usuarioLogado) {
        notificacaoService.inscrever(request, usuarioLogado);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscribe")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desinscrever(@RequestParam String endpoint) {
        notificacaoService.desinscrever(endpoint);
        return ResponseEntity.noContent().build();
    }

    // Teste manual: dispara uma notificação para todos os aparelhos de ADMIN e
    // devolve o que aconteceu com cada um (enviada, expirada, falhou...).
    @PostMapping("/teste")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificacaoService.ResultadoEnvio> testar() {
        return ResponseEntity.ok(notificacaoService.enviarTeste());
    }
}
