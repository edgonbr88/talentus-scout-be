package com.talentus.scout.web.dto;

import com.talentus.scout.domain.enums.UserRole;
import java.util.UUID;

public class RegisterTutorResponse {

    private UUID userId;
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserRole role = UserRole.TUTOR;
    private String email;
    private String fullName;

    public RegisterTutorResponse() {
    }

    public RegisterTutorResponse(UUID userId, String token, long expiresIn, String email, String fullName) {
        this.userId = userId;
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.role = UserRole.TUTOR;
        this.email = email;
        this.fullName = fullName;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
