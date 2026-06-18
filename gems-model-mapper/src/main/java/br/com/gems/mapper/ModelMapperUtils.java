package br.com.gems.mapper;

import lombok.experimental.UtilityClass;
import org.hibernate.collection.spi.PersistentCollection;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

@UtilityClass
public class ModelMapperUtils {

    private static final ModelMapper STRICT_MODEL_MAPPER;

    static {
        STRICT_MODEL_MAPPER = new ModelMapper();
        ignoreLazyFieldsNotInitialized(STRICT_MODEL_MAPPER);
        STRICT_MODEL_MAPPER.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        STRICT_MODEL_MAPPER.getConfiguration().setAmbiguityIgnored(true);
    }

    /**
     * Realiza o mapeamento utilizando a estratégia {@link org.modelmapper.convention.MatchingStrategies#STRICT}.
     * Deve ser utilizado em cenários onde o mapeamento padrão {@link org.modelmapper.convention.MatchingStrategies#STANDARD}
     * gera ambiguidades devido a nomes ou caminhos semelhantes nas entidades de destino (ex: campos aninhados com o mesmo nome).
     * <p>
     * <b>Caso de Exemplo (Ambiguidade de Responsável e Aluno):</b>
     * Se a entidade destino {@code ResponsavelAluno} possuir {@code pessoa} (objeto próprio) e {@code aluno.pessoa} (do aluno associado),
     * o mapeamento padrão pode associar a propriedade {@code pessoa} do DTO incorretamente à {@code aluno.pessoa} do destino.
     * <p>
     * Exemplo de payload JSON contendo o conflito corrigido com o mapeamento STRICT:
     * <pre>{@code
     * {
     *   "id": "c62b53bd-076d-4829-b360-8fcb53fbd1e2",
     *   "alunoId": "dd65e306-076d-4829-b360-8fcb53fbd1e2",
     *   "pessoa": {
     *     "id": "aee4fbf6-43d3-4afe-bf16-5183079ce0e5",
     *     "primeiroNome": "Maria",
     *     "sobrenome": "Silva",
     *     "numeroDocumento": "123.456.789-00",
     *     "tipoDocumento": "CPF"
     *   },
     *   "tipoRelacao": "MAE",
     *   "isResponsavelFinanceiro": true
     * }
     * }</pre>
     *
     * @param source Objeto de origem.
     * @param destinationType Classe de destino.
     * @param <D> Tipo do objeto de destino.
     * @return Instância mapeada da classe de destino.
     */
    public static <D> D mapStrict(Object source, Class<D> destinationType) {
        return STRICT_MODEL_MAPPER.map(source, destinationType);
    }

    /**
     * Copia as propriedades do objeto de origem para o objeto de destino existente utilizando
     * a estratégia {@link org.modelmapper.convention.MatchingStrategies#STRICT}.
     *
     * @param source Objeto de origem.
     * @param destination Objeto de destino existente.
     */
    public static void mapStrict(Object source, Object destination) {
        STRICT_MODEL_MAPPER.map(source, destination);
    }

    public static void ignoreLazyFieldsNotInitialized(ModelMapper modelMapper ) {
        modelMapper.getConfiguration()
                .setPropertyCondition( context -> !isAEntity( context ) || !isAEntityNotInitialized( context.getSource() ) );
    }

    private static boolean isAEntity( Object source ) {
        return source instanceof PersistentCollection;
    }

    private static boolean isAEntityNotInitialized( Object source ) {
        return isAEntity(source) && !((PersistentCollection<?>)source).wasInitialized();
    }

}
