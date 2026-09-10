package br.com.gems.tenant;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Consulta todas as {@link TenantAliasSource} registradas e devolve o alias — ou recusa.
 * <p>
 * <strong>Divergência recusa</strong> (MT-8). A alternativa natural — a primeira fonte não-vazia vence —
 * parece inofensiva e não é: no dia em que duas fontes discordarem, ela roteia o tráfego para a
 * organização que veio primeiro na ordem dos beans, em silêncio, e o operador não tem nem log para
 * suspeitar. Como a ordem dos beans não é parte do contrato de ninguém, o resultado seria um isolamento
 * que depende de detalhe de configuração do Spring.
 * </p>
 * <p>
 * Fontes que devolvem <em>o mesmo</em> alias não conflitam — é o caso comum de redundância deliberada
 * (o claim do JWT e o subdomínio concordando), e recusar ali só criaria atrito sem ganho de isolamento.
 * </p>
 */
@Slf4j
public class TenantAliasResolver {

    private final List<TenantAliasSource> sources;

    public TenantAliasResolver(List<TenantAliasSource> sources) {
        this.sources = sources;
    }

    /**
     * O alias em que todas as fontes que opinaram concordam.
     *
     * @return o alias, ou vazio se fonte alguma opinou — cabe a quem chama decidir se isso é erro.
     * @throws TenantAliasConflictException se duas fontes responderem aliases diferentes.
     */
    public Optional<String> resolve() {
        Map<String, String> aliasPorFonte = new LinkedHashMap<>();
        for (TenantAliasSource source : sources) {
            source.currentAlias()
                    .filter(alias -> !alias.isBlank())
                    .ifPresent(alias -> aliasPorFonte.put(source.sourceName(), TenantIdentifierValidator.sanitize(alias)));
        }

        Set<String> distintos = Set.copyOf(aliasPorFonte.values());
        if (distintos.size() > 1) {
            TenantAliasConflictException conflito = new TenantAliasConflictException(aliasPorFonte);
            log.error("event=TENANT_ALIAS_CONFLICT organizacao={} causa={} modulo=gems-jpa-multi-tenant",
                    TenantLogFields.ORGANIZACAO_AUSENTE, conflito.getMessage());
            throw conflito;
        }

        return distintos.stream().findFirst();
    }
}
