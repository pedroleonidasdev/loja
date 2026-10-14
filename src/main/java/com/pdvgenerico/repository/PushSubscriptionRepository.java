package com.pdvgenerico.repository;

import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    // usado na hora de disparar as notificações: todas as inscrições de
    // usuários com perfil ADMIN (independente de qual admin, ou de quantos
    // dispositivos cada um tenha ativado)
    List<PushSubscription> findByUsuario_PerfilAndUsuario_AtivoTrue(Perfil perfil);

    // usado ao excluir um usuário: as inscrições de notificação dele vão junto
    @Modifying
    @Query("delete from PushSubscription p where p.usuario.id = :id")
    int deleteByUsuarioId(@Param("id") Long id);
}
