package com.talentus.scout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.config.OAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OAuthTokenVerifierService {

    private static final long JWKS_CACHE_TTL_SECONDS = 3600L;

    private final OAuthProperties oauthProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();

    public OAuthTokenVerifierService(OAuthProperties oauthProperties, ObjectMapper objectMapper) {
        this.oauthProperties = oauthProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public VerifiedSocialClaims verifyAndExtractClaims(String provider, String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            throw new IllegalArgumentException("El token OIDC no puede estar vacío.");
        }

        String[] parts = jwtToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Formato de token JWT OIDC inválido.");
        }

        if (!oauthProperties.isVerifySignature()) {
            return extractUnverifiedClaims(parts[1], provider);
        }

        JsonNode header = decodeJwtJsonPart(parts[0], "header");
        String alg = header.path("alg").asText("");
        String kid = header.path("kid").asText("");

        if (!alg.startsWith("RS") || kid.isBlank()) {
            throw new IllegalArgumentException("Encabezado JWT OIDC inválido o algoritmo no soportado: " + alg);
        }

        RSAPublicKey publicKey = resolvePublicKey(provider, kid);

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(jwtToken)
                    .getPayload();
        } catch (JwtException e) {
            throw new IllegalArgumentException("Firma o vigencia de token JWT de " + provider + " inválida: " + e.getMessage(), e);
        }

        validateIssuerAndAudience(provider, claims);

        return buildVerifiedClaims(provider, claims);
    }

    protected String fetchJwksJson(String jwksUri) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUri))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " al consultar JWKS en " + jwksUri);
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudieron obtener las claves públicas JWKS del proveedor: " + e.getMessage(), e);
        }
    }

    public void loadJwksFromJson(String provider, String jwksJson) {
        Map<String, RSAPublicKey> parsed = parseJwksKeys(jwksJson);
        jwksCache.put(provider.toUpperCase(), new CachedJwks(parsed, Instant.now().plusSeconds(JWKS_CACHE_TTL_SECONDS)));
    }

    public void clearCache() {
        jwksCache.clear();
    }

    private RSAPublicKey resolvePublicKey(String provider, String kid) {
        String key = provider.toUpperCase();
        CachedJwks cached = jwksCache.get(key);

        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            RSAPublicKey found = cached.keys().get(kid);
            if (found != null) {
                return found;
            }
        }

        // Refresca el caché JWKS si expiró o si el proveedor rotó el kid
        String jwksUri = "GOOGLE".equals(key)
                ? oauthProperties.getGoogle().getJwksUri()
                : oauthProperties.getMicrosoft().getJwksUri();

        String jwksJson = fetchJwksJson(jwksUri);
        Map<String, RSAPublicKey> refreshedKeys = parseJwksKeys(jwksJson);
        jwksCache.put(key, new CachedJwks(refreshedKeys, Instant.now().plusSeconds(JWKS_CACHE_TTL_SECONDS)));

        RSAPublicKey publicKey = refreshedKeys.get(kid);
        if (publicKey == null) {
            throw new IllegalArgumentException("Clave pública JWKS no encontrada para kid=" + kid + " en proveedor " + provider);
        }
        return publicKey;
    }

    private Map<String, RSAPublicKey> parseJwksKeys(String jwksJson) {
        try {
            JsonNode root = objectMapper.readTree(jwksJson);
            JsonNode keysNode = root.path("keys");
            if (!keysNode.isArray()) {
                throw new IllegalArgumentException("Estructura JWKS inválida: falta arreglo 'keys'.");
            }

            Map<String, RSAPublicKey> result = new ConcurrentHashMap<>();
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            for (JsonNode keyNode : keysNode) {
                if (!"RSA".equalsIgnoreCase(keyNode.path("kty").asText())) {
                    continue;
                }
                String kid = keyNode.path("kid").asText("");
                String n = keyNode.path("n").asText("");
                String e = keyNode.path("e").asText("");
                if (kid.isBlank() || n.isBlank() || e.isBlank()) {
                    continue;
                }
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                result.put(kid, (RSAPublicKey) keyFactory.generatePublic(spec));
            }
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error al interpretar claves públicas JWKS: " + ex.getMessage(), ex);
        }
    }

    private void validateIssuerAndAudience(String provider, Claims claims) {
        String issuer = claims.getIssuer();
        if ("GOOGLE".equalsIgnoreCase(provider)) {
            if (!"https://accounts.google.com".equals(issuer) && !"accounts.google.com".equals(issuer)) {
                throw new IllegalArgumentException("Emisor (iss) de token Google no autorizado: " + issuer);
            }
            Object emailVerified = claims.get("email_verified");
            if (Boolean.FALSE.equals(emailVerified) || "false".equalsIgnoreCase(String.valueOf(emailVerified))) {
                throw new IllegalArgumentException("La cuenta de Google no tiene un correo electrónico verificado.");
            }
            validateAudience(oauthProperties.getGoogle().getClientId(), claims.getAudience(), "Google");
        } else if ("MICROSOFT".equalsIgnoreCase(provider)) {
            if (issuer == null || !issuer.startsWith("https://login.microsoftonline.com/") || !issuer.endsWith("/v2.0")) {
                throw new IllegalArgumentException("Emisor (iss) de token Microsoft no autorizado: " + issuer);
            }
            validateAudience(oauthProperties.getMicrosoft().getClientId(), claims.getAudience(), "Microsoft");
        }
    }

    private void validateAudience(String expectedClientId, Set<String> tokenAudiences, String providerLabel) {
        if (expectedClientId == null || expectedClientId.isBlank()) {
            return;
        }
        if (tokenAudiences == null || !tokenAudiences.contains(expectedClientId)) {
            throw new IllegalArgumentException("La audiencia (aud) del token " + providerLabel + " no corresponde a esta aplicación.");
        }
    }

    private VerifiedSocialClaims buildVerifiedClaims(String provider, Claims claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            sub = claims.get("oid", String.class);
        }
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("El token OIDC de " + provider + " no contiene identificador de usuario (sub/oid).");
        }

        String email = firstNonBlank(
                claims.get("email", String.class),
                claims.get("preferred_username", String.class),
                claims.get("upn", String.class)
        );
        if (email == null) {
            throw new IllegalArgumentException("El token OIDC no contiene un correo electrónico válido.");
        }

        String name = claims.get("name", String.class);
        if (name == null || name.isBlank()) {
            String givenName = claims.get("given_name", String.class);
            String familyName = claims.get("family_name", String.class);
            if (givenName != null && !givenName.isBlank()) {
                name = (familyName != null && !familyName.isBlank()) ? givenName + " " + familyName : givenName;
            } else {
                name = email.split("@")[0];
            }
        }

        return new VerifiedSocialClaims(sub, email, name);
    }

    private VerifiedSocialClaims extractUnverifiedClaims(String payloadPart, String provider) {
        JsonNode root = decodeJwtJsonPart(payloadPart, "payload");
        String sub = root.hasNonNull("sub") ? root.get("sub").asText()
                : (root.hasNonNull("oid") ? root.get("oid").asText() : null);
        String email = firstNonBlank(
                root.hasNonNull("email") ? root.get("email").asText() : null,
                root.hasNonNull("preferred_username") ? root.get("preferred_username").asText() : null,
                root.hasNonNull("upn") ? root.get("upn").asText() : null
        );
        if (sub == null || sub.isBlank() || email == null) {
            throw new IllegalArgumentException("El token no contiene los claims mínimos requeridos (sub/email).");
        }
        String name = root.hasNonNull("name") ? root.get("name").asText() : email.split("@")[0];
        return new VerifiedSocialClaims(sub, email, name);
    }

    private JsonNode decodeJwtJsonPart(String base64UrlPart, String label) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64UrlPart);
            return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("Error decodificando " + label + " del token JWT OIDC: " + e.getMessage(), e);
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }

    private record CachedJwks(Map<String, RSAPublicKey> keys, Instant expiresAt) {}

    public record VerifiedSocialClaims(String providerUserId, String email, String fullName) {}
}
