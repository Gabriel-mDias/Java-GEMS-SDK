# Quickstart de validação — 3.2.0

1. `mvn -B -pl gems-auditing -am test`: contexto vazio e habilitado; commit/rollback; sigilo.
2. Mutar actor id e correlação separadamente; cada rede deve ficar vermelha e ser restaurada.
3. `mvn -B -pl gems-keycloak-admin,gems-security-authorization -am test`: ciclo, compensação,
   snapshots, grupos, roles e composição.
4. Mutar delete compensatório, enable, snapshot/grupos, criação e composição; vermelho/restauração.
5. Compilar consumer smoke escrito contra interfaces 3.1.0 usando jars 3.2.0.
6. `mvn -B clean install -DgenerateBackupPoms=false`: contagem >= 186.
7. Em repositório Maven limpo, importar somente `gems-bom:3.2.0` e resolver
   `gems-auditing`, `gems-keycloak-admin` e `gems-security-authorization`.
8. Confirmar PR/CI, merge, publish e quatro coordenadas no registry antes de liberar C11.
