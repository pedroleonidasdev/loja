package com.pdvgenerico.repository;

import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    // usado na hora de disparar as notificações: todas as inscrições de
    // usuários com perfil ADMIN (independente de qual admin, ou de quantos
    // dispositivos cada um tenha ativado)
    List<PushSubscription> findByUsuario_PerfilAndUsuario_AtivoTrue(Perfil perfil);
}
