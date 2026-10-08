# 3.4.0 — candidato, ainda não publicado

Novo artefato independente `gems-firebase-auth` e entrada no BOM. Identidade Firebase encapsulada,
integração canônica de ações, JDBC opt-in com constraints/CTE, lifecycle e outbox duráveis.
Firebase Admin 9.11.0; nenhum requisito de Firebase real nos testes.

Ports administrativos e consultas paginadas tipadas incluem própria conta/contexto com nomes de
tenants/perfis/grupos. Delegação recalcula o ator SQL em grants diretos e heranças; bootstrap central
é explícito/auditado. Scanner/catálogo são obrigatórios no opt-in MVC. Host de serviço Docker é suportado.
REVOKE permanece barreira antes de ENABLE mesmo em versão posterior. Filas de identidade/outbox têm
backoff por Clock e lease/CAS. CREATE antes de ACK recupera por UID e impede edição durante provisioning.
Eventos identificam ator/alvo/relação/ação; recusas têm logs sanitizados e correlation ID.

JDBC acompanha a DataSource criada pelo Boot. Infraestrutura de segurança qualificada não impede
o manager empresarial automático: transações JPA sem qualificador usam JPA. Segurança usa outra
conexão do mesmo pool e confirma sua outbox independentemente do rollback empresarial, mantendo
operações aninhadas de segurança atômicas. Integração provada com Boot/Hibernate/repository reais
e PostgreSQL após migrations explícitas; JPA permanece somente no classpath de teste deste módulo.
Cadeias exatas TransactionAwareDataSourceProxy/DelegatingDataSource são resolvidas ao pool antes
da criação do recurso privado. Lazy/routing/adapters customizados/proxies JDK são recusados no
arranque: exigem override qualificado com isolamento físico comprovado pelo consumidor.
Regressões PostgreSQL verificam IDs de backend distintos, suspensão/grants/outbox duráveis após
rollback JPA, restauração de schema/holders e cleanup de falhas e concorrência com pool limitado.

`gems-jpa-multi-tenant` recebe bootstrap opt-in por `gems.tenant.validation-schema`, alias ASCII opt-in
por `gems.tenant.strict-alias`, e controle de lote `gems.tenant.liquibase.startup-enabled` (default true).
As APIs diretas da configuração, o sanitizer, naming e disciplina TenantScope legados são preservados.

Consulte [FIREBASE-AUTH.md](FIREBASE-AUTH.md) para configuração, migrations explícitas e gates.
Review independente e consumidor remoto isolado são necessários antes de declarar a release aceita.
