package com.pdvgenerico.repository;

import com.pdvgenerico.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);

    @Modifying
    @Query("update Usuario u set u.sessaoVersao = u.sessaoVersao + 1 where u.id = :id")
    int incrementarSessaoVersao(@Param("id") Long id);

    @Modifying
    @Query("update Usuario u set u.sessaoVersao = u.sessaoVersao + 1")
    int incrementarSessaoVersaoDeTodos();
}
