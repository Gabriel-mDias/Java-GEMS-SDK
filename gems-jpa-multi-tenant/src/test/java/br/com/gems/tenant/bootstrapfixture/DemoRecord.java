package br.com.gems.tenant.bootstrapfixture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Entidade mínima de teste sem schema fixo. */
@Entity @Table(name = "demo_record")
public class DemoRecord {
    @Id private UUID id;
    @Column(name = "nm_record") private String name;
    protected DemoRecord() { }
    public DemoRecord(UUID id, String name) { this.id = id; this.name = name; }
    public String getName() { return name; }
}
