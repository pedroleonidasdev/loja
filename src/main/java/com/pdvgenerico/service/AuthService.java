package com.pdvgenerico.service;

import com.pdvgenerico.dto.LoginRequest;
import com.pdvgenerico.dto.LoginResponse;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    public LoginResponse login(LoginRequest request) {
        // bloqueia antes de sequer testar a senha, se esse login errou demais
        loginAttemptService.verificarBloqueio(request.login());

        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.login(), request.senha()));

            Usuario usuario = (Usuario) authentication.getPrincipal();
            String token = jwtUtil.generateToken(usuario);

            loginAttemptService.registrarSucesso(request.login());
            return new LoginResponse(token, usuario.getLogin(), usuario.getNome(), usuario.getPerfil().name());
        } catch (BadCredentialsException e) {
            loginAttemptService.registrarFalha(request.login());
            throw e;
        }
    }
}
