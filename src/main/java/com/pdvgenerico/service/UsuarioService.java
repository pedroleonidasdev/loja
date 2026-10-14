package com.pdvgenerico.service;

import com.pdvgenerico.dto.UsuarioRequest;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.PushSubscriptionRepository;
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
    private final PushSubscriptionRepository pushSubscriptionRepository;
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

    /**
     * Exclui o usuário de vez. Só é permitido quando ele não tem nenhum registro
     * no sistema (vendas, caixas, trocas, consignações, despesas); caso contrário
     * apagar quebraria o histórico e o caminho é desativar (campo "Usuário ativo").
     */
    @Transactional
    public void excluir(Long id, String loginChamador) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id " + id));

        if (usuario.getLogin().equals(loginChamador)) {
            throw new BusinessException("Você não pode excluir o seu próprio usuário.");
        }
        if (usuario.getPerfil() == Perfil.ADMIN && usuarioRepository.countByPerfil(Perfil.ADMIN) <= 1) {
            throw new BusinessException("Não é possível excluir o único administrador do sistema.");
        }
        if (temRegistros(id)) {
            throw new BusinessException(
                    "Este usuário possui registros no sistema (vendas, caixas, trocas, consignações ou despesas) "
                            + "e não pode ser excluído. Para impedir o acesso, desative-o em Editar > Usuário ativo.");
        }

        pushSubscriptionRepository.deleteByUsuarioId(id);
        usuarioRepository.delete(usuario);
    }

    private boolean temRegistros(Long id) {
        String[] consultas = {
                "select count(e) from Venda e where e.usuario.id = :id",
                "select count(e) from Troca e where e.usuario.id = :id",
                "select count(e) from Consignacao e where e.usuario.id = :id",
                "select count(e) from Despesa e where e.usuario.id = :id",
                "select count(e) from Caixa e"
                        + " left join e.usuarioAbertura a left join e.usuarioFechamento f"
                        + " left join e.usuarioReabertura r left join e.usuarioEdicaoAbertura d"
                        + " where a.id = :id or f.id = :id or r.id = :id or d.id = :id"
        };
        for (String jpql : consultas) {
            Long total = entityManager.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
            if (total != null && total > 0) return true;
        }
        return false;
    }
}
