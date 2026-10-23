package com.pdvgenerico.controller;

import com.pdvgenerico.dto.LoginRequest;
import com.pdvgenerico.dto.LoginResponse;
import com.pdvgenerico.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.pdvgenerico.model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Quem é o dono do token desta requisição. O frontend usa isto para corrigir o nome/login
     * exibidos caso o que ficou guardado no navegador não seja o mesmo usuário do token.
     */
    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new LoginResponse(null, usuario.getLogin(), usuario.getNome(), usuario.getPerfil().name()));
    }
}
