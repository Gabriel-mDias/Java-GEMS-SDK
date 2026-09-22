# Pacote de Execução — Rodada 001, SDK-AUD

| Controle | Valor |
| :--- | :--- |
| Checkpoint | `SDK-AUD` |
| Pré-requisitos | `SDK-P00` aprovado em `8e43585` |
| Política de sessão | `nova` |
| SHA anterior | `8e43585914eec2371dfaff03c36fbca9abe66fba` |
| Estado inicial | `aberto` |
| Gate focado | raiz; `mvn -B -pl gems-auditing -am test` |
| Tier de capacidade | executor especializado |
| Esforço mínimo | `medium`; oito tasks coesas e quatro mutações isoladas |
| Risco | alto — API pública aditiva, compatibilidade de schema e atomicidade transacional |
| Decisão | delegar |
| Comparação de custo | 3 tipos públicos, 3 integrações, redes H2/ORM e 4 mutações superam pacote, onboarding, revisão e novo gate |

> A lista `READ` é fechada. Nada mais pode ser lido ou pesquisado. Se faltar contexto, pare e
> reporte. Não comite. Antes da primeira edição, crie e preencha a §1 de
> `estado-lote-SDK-AUD.md`; atualize-o a cada fronteira de tarefa. Rode o portão da §5.

## 1. Fronteira fechada do lote

Implementar somente T002–T009 do contrato de auditoria 3.2.0. Não iniciar SDK-KC/SDK-REL, não
alterar versão, documentação de release, POM, contrato, tasks ou roteiro.

### READ

- `specs/001-auditoria-keycloak-3.2.0/pacote-de-execucao-SDK-AUD.md`
- `specs/001-auditoria-keycloak-3.2.0/ancora-porcelain-SDK-AUD.txt`
- `specs/001-auditoria-keycloak-3.2.0/contracts/auditing-3.2.0.md`
- `CLAUDE.md`
- `gems-auditing/src/main/java/br/com/gems/auditing/AuditActor.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/AuditActorProvider.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/SystemAuditActorProvider.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/TransactionalAuditWriter.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/HibernateAuditListener.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/AuditingAutoConfiguration.java`
- `gems-auditing/src/test/java/br/com/gems/auditing/SuperficiePublicaTest.java`
- `gems-auditing/src/test/java/br/com/gems/auditing/TransactionalAuditWriterTest.java`
- `gems-auditing/src/test/java/br/com/gems/auditing/AuditWithoutDomainCallTest.java`
- `D:/repos/Meduc/meduc-workspace/.agents/skills/model-delegation/references/package-contract.md`
- `D:/repos/Meduc/meduc-workspace/.specify/templates/estado-lote-template.md`

### Regras e instruções consumidas

- `CLAUDE.md` — instrução local; o repositório não possui `AGENTS.md` nem constituição.

### EDIT / CREATE

| # | Ação | Arquivo |
| :-- | :--- | :--- |
| T002 | EDIT | `gems-auditing/src/test/java/br/com/gems/auditing/SuperficiePublicaTest.java` |
| T003 | CREATE | `gems-auditing/src/test/java/br/com/gems/auditing/AuditContextTest.java` |
| T004 | CREATE | `gems-auditing/src/main/java/br/com/gems/auditing/AuditContext.java` `gems-auditing/src/main/java/br/com/gems/auditing/AuditContextProvider.java` `gems-auditing/src/main/java/br/com/gems/auditing/EmptyAuditContextProvider.java` |
| T005 | EDIT | `gems-auditing/src/test/java/br/com/gems/auditing/TransactionalAuditWriterTest.java` `gems-auditing/src/test/java/br/com/gems/auditing/AuditWithoutDomainCallTest.java` |
| T006 | EDIT | `gems-auditing/src/main/java/br/com/gems/auditing/TransactionalAuditWriter.java` |
| T007 | EDIT | `gems-auditing/src/main/java/br/com/gems/auditing/HibernateAuditListener.java` |
| T008 | EDIT | `gems-auditing/src/main/java/br/com/gems/auditing/AuditingAutoConfiguration.java` |
| SDK-AUD | CREATE | `specs/001-auditoria-keycloak-3.2.0/estado-lote-SDK-AUD.md` |

## 2. O que já existe e você consome

```java
public record AuditContext(String actorId, String correlationId) {
    public static AuditContext empty();
}
@FunctionalInterface
public interface AuditContextProvider { AuditContext currentContext(); }
```

`null` do provider vira `AuditContext.empty()`; strings vazias viram `null`. A propriedade
`gems.auditing.context-columns-enabled` é `false` por padrão. Desligada, usa exatamente o SQL 3.1.0;
ligada, inclui `ID_ACTOR` e `CD_CORRELATION` na mesma conexão. Falha de provider sobe; schema sem
colunas com opt-in ligado vira `AuditWriteException`. `AuditActor` e contratos existentes não mudam.

## 3. Arquivos de referência — copie destes

| Para escrever | Copie de |
| :--- | :--- |
| record público/Javadoc/normalização | `gems-auditing/src/main/java/br/com/gems/auditing/AuditActor.java` |
| provider público e default | `gems-auditing/src/main/java/br/com/gems/auditing/AuditActorProvider.java` e `SystemAuditActorProvider.java` |
| teste H2 transacional | `gems-auditing/src/test/java/br/com/gems/auditing/TransactionalAuditWriterTest.java` |
| teste ORM real | `gems-auditing/src/test/java/br/com/gems/auditing/AuditWithoutDomainCallTest.java` |

## 4. As regras que valem para este lote

1. Testes T002/T003/T005 ficam vermelhos antes da implementação correspondente.
2. `AuditActor`, `AuditActorProvider` e `AuditTrailDestination` ficam inalterados.
3. Tipos públicos novos têm Javadoc pt-BR; writer/listener continuam package-private.
4. Contexto normaliza brancos para `null`; provider `null` vira vazio, mas exceção não é engolida.
5. Listener consulta actor e contexto uma vez por operação e passa ambos ao writer.
6. Default seguro não inventa identidade e o SQL 3.1.0 não referencia colunas novas.
7. Opt-in grava as duas colunas na mesma conexão e rollback remove cabeçalho, mudanças e contexto.
8. Não adicionar `DataSource`, transação, dependência ou auto-configuração de credencial.
9. T009 muta isoladamente id, correlação, opt-in e atomicidade; cada rede fica vermelha, é restaurada por edição inversa e volta a verde.
10. Não editar fora da tabela; contexto ausente exige parada, não descoberta de escopo.

## 5. O portão

Na raiz:

```powershell
mvn -B -pl gems-auditing -am test
```

Esperado: exit `0`, zero falhas/erros/skips e contagem do módulo nunca abaixo de 27. Depois das
quatro mutações restauradas, repetir o mesmo gate e `git diff --check`.

## 6. O relatório

Relate: arquivos; feito/não feito; vermelho test-first; quatro mutações e falhas exatas; comandos,
exit e resumo; divergências pacote×código. O relatório não substitui o estado persistido.
