package br.com.gems.firebase.auth.consumer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Entidade empresarial do consumidor de teste, sem tipos da SDK no modelo. */
@Entity
@Table(name = "firebase_business_record", schema = "public")
public class BusinessRecord {
    @Id private UUID id;
    private String name;
    protected BusinessRecord() { }
    public BusinessRecord(UUID id, String name) { this.id = id; this.name = name; }
    public UUID getId() { return id; }
    public String getName() { return name; }
}
