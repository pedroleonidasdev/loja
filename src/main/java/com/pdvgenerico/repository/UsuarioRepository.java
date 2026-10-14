package com.pdvgenerico.repository;

import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);

    long countByPerfil(Perfil perfil);

    List<Usuario> findByAtivoTrueAndUltimoAcessoAfterOrderByNomeAsc(LocalDateTime desde);

    @Modifying
    @Query("update Usuario u set u.sessaoVersao = u.sessaoVersao + 1, u.ultimoAcesso = null where u.id = :id")
    int incrementarSessaoVersao(@Param("id") Long id);

    @Modifying
    @Query("update Usuario u set u.sessaoVersao = u.sessaoVersao + 1, u.ultimoAcesso = null")
    int incrementarSessaoVersaoDeTodos();

    @Modifying
    @Query("update Usuario u set u.ultimoAcesso = :quando where u.id = :id")
    int atualizarUltimoAcesso(@Param("id") Long id, @Param("quando") LocalDateTime quando);
}
