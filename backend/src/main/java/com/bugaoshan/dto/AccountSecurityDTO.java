package com.bugaoshan.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountSecurityDTO {
    private AccountSecurityDTO() {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BindingCode(@NotBlank String oldPassword,
            @NotBlank @Email @Size(max=254) String email) {
        public BindingCode { email=trim(email); }
    }
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Binding(@NotBlank String oldPassword,
            @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String requestId,
            @NotBlank @Pattern(regexp="[0-9]{6}") String code) {
        public Binding { email=trim(email); }
    }
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Password(@NotBlank String oldPassword,
            @NotBlank @Size(min=6,max=20) String newPassword) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ResetCode(@NotBlank @Size(min=3,max=20) String username,
            @NotBlank @Email @Size(max=254) String email) {
        public ResetCode { username=trim(username); email=trim(email); }
    }
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Reset(@NotBlank @Size(min=3,max=20) String username,
            @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String requestId,
            @NotBlank @Pattern(regexp="[0-9]{6}") String code,
            @NotBlank @Size(min=6,max=20) String newPassword) {
        public Reset { username=trim(username); email=trim(email); }
    }
    private static String trim(String value) { return value==null?null:value.trim(); }
}
