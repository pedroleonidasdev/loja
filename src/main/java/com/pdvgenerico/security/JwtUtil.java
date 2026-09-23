package com.pdvgenerico.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    // mesmo valor do fallback em application.properties (app.jwt.secret) — serve
    // só pra detectar, no boot, se a variável de ambiente JWT_SECRET não foi
    // definida e avisar bem alto, já que esse valor está público no código-fonte.
    private static final String SEGREDO_PADRAO_INSEGURO =
            "troque-esta-chave-secreta-em-producao-para-algo-bem-mais-longo-e-aleatorio-1234567890";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @PostConstruct
    void validarSegredo() {
        if (SEGREDO_PADRAO_INSEGURO.equals(secret)) {
            log.warn("############################################################################");
            log.warn("# ATENÇÃO: a aplicação está usando o segredo JWT padrão (definido no código #");
            log.warn("# fonte). Isso permite forjar tokens válidos. Defina a variável de ambiente  #");
            log.warn("# JWT_SECRET com um valor longo e aleatório antes de ir para produção.        #");
            log.warn("############################################################################");
        } else if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // HS256 exige uma chave de pelo menos 256 bits (32 bytes); chaves menores
            // são rejeitadas em tempo de execução, então é melhor avisar cedo.
            log.warn("O segredo JWT (JWT_SECRET) tem menos de 32 bytes — recomenda-se uma chave " +
                    "mais longa e aleatória para uso seguro com HS256.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        String roles = userDetails.getAuthorities().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
