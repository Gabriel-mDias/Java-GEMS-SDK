# Release gates — candidata de implementação

A implementação candidata está em `work/firebase-auth-sdk`, sem commit/publicação pelo executor.
Os comandos, fingerprints, contagens e resultados observados de cada tentativa ficam no ledger externo
`java-state.json` e no relatório de implementação do controlador. Esta lista é o contrato de release,
não uma declaração de que publicação/consumidor/review foram aprovados.

1. Ports/verifier/Admin/Spring/customresolver semDS; JDBCschema opt-in semJPAMulti/Obs/AWS/adapter anterior.
2. Authentication/context/scanner e revocation real; constraints/CTE/outbox de lifecycle/Clock sanitização.
3. Helpers JPAbootstrap modelo opt-in; legacyproperties/sanitize/naming preservados; gate compatibilidade.
4. BOM/docs/changelog/release com versão candidata3.4.0 somente se aditiva; full clean verify e Auth Emulator.
5. Após aprovação humana, publicar pelo workflow `Release` (`.github/workflows/release.yml`), no CI Ubuntu,
   disparado pelo push da tag imutável `v3.4.0`. Confirmar tag/versão do POM e que a tag aponta para o SHA
   atual revisado; nesse SHA, `mvn clean verify` e Auth Emulator devem passar antes de `mvn deploy`.
   `Main Validation` (`.github/workflows/publish.yml`) somente valida main; nenhum dos dois oferece
   `workflow_dispatch`. Depois da publicação autorizada, consumidor Maven em M2 novo resolve artefato
   REMOTO e executa smoke independente antes da adoção no sample.
6. Antes de substituir no sample: dependencytree/alcance medido, garantia nomeada, equivalência e mutação
   no consumidor, nenhuma duplicata coexistente. Installlocal não comprova publicação nem aceite.

Review independente e aceitação são etapas externas ao executor. Publicação e consumidor remoto
permanecem NOT_RUN até execução autorizada depois dessa revisão. O gate local do emulator exige
Firebase CLI 15.32.1 fornecida por `GEMS_FIREBASE_CLI_JS`; instalação global não é aceita.

A regressão de isolamento JDBC inclui consumidor Boot com TransactionAwareDataSourceProxy,
cadeias transparentes exatas, IDs PostgreSQL distintos, rollback empresarial e commit durável
local/outbox, cleanup de falha e pool limitado concorrente. Lazy/routing/adapters opacos falham no
arranque automático; override direto qualificado é responsabilidade explícita do consumidor,
conforme os limites documentados em FIREBASE-AUTH.md. A resolução não descarta semântica de
read-only/routing e não usa reflection para descobrir alvos privados.
