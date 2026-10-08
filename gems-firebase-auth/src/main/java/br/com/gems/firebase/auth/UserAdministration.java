package br.com.gems.firebase.auth;

import java.util.UUID;

/** Lifecycle global de usuários, restrito a ações centrais concretas ou bootstrap explícito. */
public interface UserAdministration {
    UUID preProvisionGoogle(String name, String email);
    UUID preProvisionGoogle(String name, String email, String selfAction);
    UUID provisionPassword(String name, String email);
    UUID provisionPassword(String name, String email, String selfAction);
    void update(UUID user, String name, String email);
    void suspend(UUID user);
    void requestReactivation(UUID user);
    void cancelReactivation(UUID user);
}
