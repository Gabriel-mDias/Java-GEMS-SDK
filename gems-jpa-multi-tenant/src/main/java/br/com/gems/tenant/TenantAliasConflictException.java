package br.com.gems.tenant;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lançada quando duas ou mais {@link TenantAliasSource} respondem aliases diferentes na mesma
 * resolução.
 * <p>
 * É a recusa contratada em MT-8. A alternativa — deixar a primeira fonte não-vazia vencer — rotearia
 * tráfego para a organização errada em silêncio sempre que alguém ordenasse a lista de forma errada.
 * Duas respostas diferentes é o mesmo que resposta nenhuma: ninguém escolhe organização por sorteio de
 * ordem de bean.
 * </p>
 * <p>
 * A mensagem nomeia cada fonte e o que ela respondeu, porque sem isso o operador não tem como saber
 * qual das duas está errada.
 * </p>
 */
public class TenantAliasConflictException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public TenantAliasConflictException(Map<String, String> aliasPorFonte) {
        super("Fontes de alias divergentes — a resolução do tenant foi recusada: "
                + aliasPorFonte.entrySet().stream()
                        .map(entrada -> entrada.getKey() + "=" + entrada.getValue())
                        .sorted()
                        .collect(Collectors.joining(", ")));
    }
}
