# Release notes - 3.3.0

## 3.3.0 (MINOR)

- Adiciona `KeycloakRealmGroupGateway`, sem alterar as interfaces publicadas em 3.2.0.
- Busca grupos de primeiro nível do realm por nome exato e sem expor tipos do cliente Keycloak.
- Cria grupos com tratamento idempotente de concorrência HTTP 409.
- Mapeia realm roles em grupos somente quando ainda não são efetivas, preservando concessões extras.
- Adiciona Spotless, Checkstyle e ArchUnit ao gate `mvn verify`.
- Restaura o ModelMapper 3.2.4, evitando a regressão de proxies para mapeamentos explícitos
  observada no JDK 26 com a versão 3.2.6; um teste de contrato protege o cenário.
- Separa validação da `main` de publicação: somente uma tag imutável `vX.Y.Z` publica pacotes e
  cria o GitHub Release.

Não há auto-configuração ou credencial padrão. A superfície 3.2.0 continua compilando sem
alteração; a nova interface é aditiva e implementada pelo mesmo `KeycloakAdminRestClient`.
