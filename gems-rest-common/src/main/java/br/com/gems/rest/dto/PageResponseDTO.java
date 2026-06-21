package br.com.gems.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Wrapper de paginação independente do Spring Data, próprio para serialização em APIs.
 * <p>
 * Expõe apenas os campos relevantes de uma {@link Page}, evitando vazar a estrutura
 * interna (e instável para serialização) de {@code PageImpl} no contrato da API.
 * </p>
 *
 * @param <T> tipo dos elementos da página.
 */
@Getter
@Builder
@AllArgsConstructor
public class PageResponseDTO<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    /**
     * Converte uma {@link Page} do Spring Data no envelope de paginação da API.
     */
    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return PageResponseDTO.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
