package com.talentus.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.config.OAuthProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OAuthTokenVerifierServiceTest {

    private OAuthProperties oauthProperties;
    private OAuthTokenVerifierService verifierService;
    private KeyPair validKeyPair;
    private KeyPair attackerKeyPair;
    private static final String TEST_KID = "test-key-2026";
    private static final String GOOGLE_CLIENT_ID = "123456-test.apps.googleusercontent.com";
    private static final String MICROSOFT_CLIENT_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        validKeyPair = kpg.generateKeyPair();
        attackerKeyPair = kpg.generateKeyPair();

        oauthProperties = new OAuthProperties();
        oauthProperties.setVerifySignature(true);
        oauthProperties.getGoogle().setClientId(GOOGLE_CLIENT_ID);
        oauthProperties.getMicrosoft().setClientId(MICROSOFT_CLIENT_ID);

        ObjectMapper mapper = new ObjectMapper();
        verifierService = new OAuthTokenVerifierService(oauthProperties, mapper);

        RSAPublicKey pub = (RSAPublicKey) validKeyPair.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray());

        String jwksJson = mapper.writeValueAsString(Map.of(
                "keys", List.of(Map.of(
                        "kty", "RSA",
                        "alg", "RS256",
                        "use", "sig",
                        "kid", TEST_KID,
                        "n", n,
                        "e", e
                ))
        ));

        verifierService.loadJwksFromJson("GOOGLE", jwksJson);
        verifierService.loadJwksFromJson("MICROSOFT", jwksJson);
    }

    @Test
    @DisplayName("Google ID Token RS256 válido: verifica firma, iss, aud y extrae claims")
    void shouldVerifyValidGoogleToken() {
        String token = Jwts.builder()
                .header().keyId(TEST_KID).and()
                .issuer("https://accounts.google.com")
                .subject("google-user-001")
                .audience().add(GOOGLE_CLIENT_ID).and()
                .claim("email", "atleta.real@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Atleta Real")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(validKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        OAuthTokenVerifierService.VerifiedSocialClaims claims = verifierService.verifyAndExtractClaims("GOOGLE", token);

        assertEquals("google-user-001", claims.providerUserId());
        assertEquals("atleta.real@gmail.com", claims.email());
        assertEquals("Atleta Real", claims.fullName());
    }

    @Test
    @DisplayName("Token manipulado o firmado con clave privada no autorizada: rechaza firma")
    void shouldRejectTamperedOrUntrustedSignature() {
        String forgedToken = Jwts.builder()
                .header().keyId(TEST_KID).and()
                .issuer("https://accounts.google.com")
                .subject("spoof-sub")
                .audience().add(GOOGLE_CLIENT_ID).and()
                .claim("email", "admin@talentusscout.com")
                .claim("email_verified", true)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(attackerKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThrows(IllegalArgumentException.class, () ->
                verifierService.verifyAndExtractClaims("GOOGLE", forgedToken));
    }

    @Test
    @DisplayName("Token expirado: rechaza por expiración")
    void shouldRejectExpiredToken() {
        String expiredToken = Jwts.builder()
                .header().keyId(TEST_KID).and()
                .issuer("https://accounts.google.com")
                .subject("google-user-001")
                .audience().add(GOOGLE_CLIENT_ID).and()
                .claim("email", "atleta.real@gmail.com")
                .claim("email_verified", true)
                .issuedAt(Date.from(Instant.now().minusSeconds(600)))
                .expiration(Date.from(Instant.now().minusSeconds(120)))
                .signWith(validKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThrows(IllegalArgumentException.class, () ->
                verifierService.verifyAndExtractClaims("GOOGLE", expiredToken));
    }

    @Test
    @DisplayName("Audiencia (aud) de otro cliente OAuth: rechaza token")
    void shouldRejectWrongAudience() {
        String wrongAudToken = Jwts.builder()
                .header().keyId(TEST_KID).and()
                .issuer("https://accounts.google.com")
                .subject("google-user-001")
                .audience().add("otro-client-id.apps.googleusercontent.com").and()
                .claim("email", "atleta.real@gmail.com")
                .claim("email_verified", true)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(validKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThrows(IllegalArgumentException.class, () ->
                verifierService.verifyAndExtractClaims("GOOGLE", wrongAudToken));
    }

    @Test
    @DisplayName("Google email_verified = false: rechaza cuenta no verificada")
    void shouldRejectUnverifiedEmail() {
        String unverifiedToken = Jwts.builder()
                .header().keyId(TEST_KID).and()
                .issuer("https://accounts.google.com")
                .subject("google-user-001")
                .audience().add(GOOGLE_CLIENT_ID).and()
                .claim("email", "sin.verificar@gmail.com")
                .claim("email_verified", false)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(validKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThrows(IllegalArgumentException.class, () ->
                verifierService.verifyAndExtractClaims("GOOGLE", unverifiedToken));
    }

    @Test
    @DisplayName("Microsoft ID Token RS256 válido: verifica firma, iss tenant v2.0 y aud")
    void shouldVerifyValidMicrosoftToken() {
        String token = Jwts.builder()
                .header().keyId(TEST_KID).and()
                .issuer("https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0")
                .subject("ms-sub-999")
                .audience().add(MICROSOFT_CLIENT_ID).and()
                .claim("preferred_username", "scout.oficial@outlook.com")
                .claim("name", "Scout Oficial")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(validKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        OAuthTokenVerifierService.VerifiedSocialClaims claims = verifierService.verifyAndExtractClaims("MICROSOFT", token);

        assertEquals("ms-sub-999", claims.providerUserId());
        assertEquals("scout.oficial@outlook.com", claims.email());
        assertEquals("Scout Oficial", claims.fullName());
    }
}
