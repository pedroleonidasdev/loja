package com.pdvgenerico.controller;

import com.pdvgenerico.dto.EncerrarSessoesResponse;
import com.pdvgenerico.dto.UsuarioRequest;
import com.pdvgenerico.dto.UsuarioResponse;
import com.pdvgenerico.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listarTodos().stream()
                .map(UsuarioResponse::fromEntity)
                .toList();
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request) {
        return ResponseEntity.ok(UsuarioResponse.fromEntity(usuarioService.criar(request)));
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request) {
        return UsuarioResponse.fromEntity(usuarioService.atualizar(id, request));
    }

    @PostMapping("/{id}/encerrar-sessoes")
    public EncerrarSessoesResponse encerrarSessoes(@PathVariable Long id, Authentication authentication) {
        return new EncerrarSessoesResponse(usuarioService.encerrarSessoes(id, authentication.getName()));
    }

    @PostMapping("/encerrar-sessoes")
    public EncerrarSessoesResponse encerrarTodasSessoes(Authentication authentication) {
        return new EncerrarSessoesResponse(usuarioService.encerrarTodasSessoes(authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, Authentication authentication) {
        usuarioService.excluir(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
