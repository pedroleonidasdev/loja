package com.pdvgenerico.service;

import com.pdvgenerico.dto.UsuarioRequest;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.UsuarioRepository;
import com.pdvgenerico.security.JwtUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EntityManager entityManager;

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario criar(UsuarioRequest request) {
        if (usuarioRepository.findByLogin(request.login()).isPresent()) {
            throw new BusinessException("Já existe um usuário com este login");
        }
        if (request.senha() == null || request.senha().isBlank()) {
            throw new BusinessException("Senha é obrigatória para novo usuário");
        }

        Usuario usuario = Usuario.builder()
                .login(request.login())
                .senha(passwordEncoder.encode(request.senha()))
                .nome(request.nome())
                .perfil(request.perfil())
                .ativo(request.ativo() == null || request.ativo())
                .build();

        return usuarioRepository.save(usuario);
    }

    public Usuario atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id " + id));

        usuario.setNome(request.nome());
        usuario.setPerfil(request.perfil());
        if (request.ativo() != null) {
            usuario.setAtivo(request.ativo());
        }
        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.senha()));
        }

        return usuarioRepository.save(usuario);
    }

    /**
     * Invalida todos os tokens já emitidos para o usuário. Se quem chamou foi
     * atingido (derrubou a própria conta), devolve um token novo para ele
     * continuar logado onde está; caso contrário, devolve null.
     */
    @Transactional
    public String encerrarSessoes(Long id, String loginChamador) {
        Usuario alvo = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id " + id));
        usuarioRepository.incrementarSessaoVersao(id);
        return alvo.getLogin().equals(loginChamador) ? tokenNovoPara(loginChamador) : null;
    }

    /** Invalida os tokens de todos os usuários, preservando a sessão de quem chamou. */
    @Transactional
    public String encerrarTodasSessoes(String loginChamador) {
        usuarioRepository.incrementarSessaoVersaoDeTodos();
        return tokenNovoPara(loginChamador);
    }

    private String tokenNovoPara(String login) {
        Usuario atualizado = usuarioRepository.findByLogin(login)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + login));
        // o UPDATE em lote não atualiza entidades já carregadas no contexto
        entityManager.refresh(atualizado);
        return jwtUtil.generateToken(atualizado);
    }

    public void excluir(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id " + id));
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }
}
