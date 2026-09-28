package com.pdvgenerico.service;

import com.pdvgenerico.dto.PushSubscriptionRequest;
import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.PushSubscription;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Envia notificações push (Web Push / VAPID) para o navegador/celular dos
 * usuários ADMIN sempre que uma venda é registrada ou o caixa é
 * aberto/fechado. Não lança exceção se o envio falhar — notificação é um
 * "extra", nunca deve derrubar a operação principal (a venda já foi salva
 * de qualquer forma).
 */
@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushService pushService;
    private final boolean configurado;

    public NotificacaoService(
            PushSubscriptionRepository pushSubscriptionRepository,
            @Value("${app.vapid.public-key:}") String vapidPublicKey,
            @Value("${app.vapid.private-key:}") String vapidPrivateKey,
            @Value("${app.vapid.subject:mailto:contato@lojapresentededeus.com.br}") String vapidSubject
    ) throws GeneralSecurityException {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.configurado = !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank();

        Security.addProvider(new BouncyCastleProvider());

        if (configurado) {
            PushService service = new PushService();
            service.setPublicKey(vapidPublicKey);
            service.setPrivateKey(vapidPrivateKey);
            service.setSubject(vapidSubject);
            this.pushService = service;
        } else {
            this.pushService = null;
        }
    }

    @PostConstruct
    void avisarSeNaoConfigurado() {
        if (!configurado) {
            log.warn("Notificações push desativadas: defina VAPID_PUBLIC_KEY e VAPID_PRIVATE_KEY " +
                    "nas variáveis de ambiente para habilitar (veja instruções no README).");
        }
    }

    @Transactional
    public void inscrever(PushSubscriptionRequest request, Usuario usuarioLogado) {
        pushSubscriptionRepository.findByEndpoint(request.endpoint()).ifPresentOrElse(
                existente -> {
                    // mesmo endpoint podendo re-inscrever (ex: trocou de usuário no mesmo
                    // navegador) — atualiza o dono em vez de duplicar
                    existente.setUsuario(usuarioLogado);
                    existente.setP256dh(request.keys().p256dh());
                    existente.setAuth(request.keys().auth());
                    pushSubscriptionRepository.save(existente);
                },
                () -> pushSubscriptionRepository.save(PushSubscription.builder()
                        .usuario(usuarioLogado)
                        .endpoint(request.endpoint())
                        .p256dh(request.keys().p256dh())
                        .auth(request.keys().auth())
                        .criadoEm(LocalDateTime.now())
                        .build())
        );
    }

    @Transactional
    public void desinscrever(String endpoint) {
        pushSubscriptionRepository.findByEndpoint(endpoint).ifPresent(pushSubscriptionRepository::delete);
    }

    /**
     * Envia uma notificação para todos os ADMINs ativos, em todos os
     * dispositivos que cada um tiver inscrito — exceto para quem realizou a
     * própria ação (ela já vê o resultado na tela, não precisa do push).
     */
    @Transactional
    public void notificarAdmins(String titulo, String corpo, String url, Long usuarioIgnorarId) {
        if (!configurado) return;

        List<PushSubscription> inscricoes = pushSubscriptionRepository
                .findByUsuario_PerfilAndUsuario_AtivoTrue(Perfil.ADMIN);

        String payload = montarPayloadJson(titulo, corpo, url);

        for (PushSubscription inscricao : inscricoes) {
            if (usuarioIgnorarId != null && inscricao.getUsuario().getId().equals(usuarioIgnorarId)) {
                continue;
            }
            enviar(inscricao, payload);
        }
    }

    private void enviar(PushSubscription inscricao, String payload) {
        try {
            Subscription subscription = new Subscription(
                    inscricao.getEndpoint(),
                    new Subscription.Keys(inscricao.getP256dh(), inscricao.getAuth())
            );
            Notification notification = new Notification(subscription, payload);
            var resposta = pushService.send(notification);

            int status = resposta.getStatusLine().getStatusCode();
            // 404/410: o navegador cancelou essa inscrição (app desinstalado, permissão
            // revogada etc.) — não faz sentido continuar tentando enviar pra ela
            if (status == 404 || status == 410) {
                pushSubscriptionRepository.delete(inscricao);
            }
        } catch (Exception e) {
            // notificação é um "extra" — uma falha de envio (rede, provedor fora do ar
            // etc.) nunca deve derrubar a venda/fechamento de caixa que já foi salvo
            log.warn("Falha ao enviar notificação push (endpoint {}): {}", inscricao.getEndpoint(), e.getMessage());
        }
    }

    private String montarPayloadJson(String titulo, String corpo, String url) {
        // JSON simples montado na mão pra não puxar mais uma dependência só pra isso;
        // os três campos são texto controlado pelo próprio backend (não vem do usuário
        // final), mas escapamos aspas/quebras de linha por segurança mesmo assim.
        return "{\"titulo\":\"" + escapar(titulo) + "\",\"corpo\":\"" + escapar(corpo) + "\",\"url\":\"" + escapar(url) + "\"}";
    }

    private String escapar(String texto) {
        return texto == null ? "" : texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
