package br.com.gems.aws.web;

import br.com.gems.aws.dto.GenerateUploadUrlRequestDTO;
import br.com.gems.aws.dto.PresignedUrlResponseDTO;
import br.com.gems.aws.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST responsável por operações de integração com AWS S3.
 * <p>
 * Fornece endpoints públicos ou protegidos para geração de URLs pré-assinadas
 * (presigned URLs) e exclusão de arquivos armazenados nos buckets do S3,
 * otimizando o tráfego evitando que o binário passe pelo servidor da aplicação.
 * </p>
 */
@RestController
@RequestMapping("/api/aws/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    /**
     * Endpoint para gerar uma URL pré-assinada que autoriza o upload direto (PUT) de um arquivo para o S3.
     *
     * @param request Objeto contendo os metadados do arquivo (nome, contentType, diretório).
     * @return {@link ResponseEntity} contendo a DTO com a URL de upload e a chave de destino no S3.
     */
    @PostMapping("/generate-upload-url")
    public ResponseEntity<PresignedUrlResponseDTO> generateUploadUrl(@RequestBody GenerateUploadUrlRequestDTO request) {
        return ResponseEntity.ok(s3Service.generatePresignedUploadUrl(request));
    }

    /**
     * Endpoint para gerar uma URL pré-assinada de download temporário para um arquivo protegido no S3.
     *
     * @param fileKey Chave exata do arquivo no S3.
     * @return {@link ResponseEntity} contendo a DTO com a URL válida temporariamente para download.
     */
    @GetMapping("/generate-download-url")
    public ResponseEntity<PresignedUrlResponseDTO> generateDownloadUrl(@RequestParam String fileKey) {
        return ResponseEntity.ok(s3Service.generatePresignedDownloadUrl(fileKey));
    }

    /**
     * Endpoint para excluir permanentemente um arquivo armazenado no bucket S3.
     *
     * @param fileKey Chave do arquivo que será excluído.
     * @return {@link ResponseEntity} vazio com status 204 (No Content) indicando sucesso.
     */
    @DeleteMapping("/delete-file")
    public ResponseEntity<Void> deleteFile(@RequestParam String fileKey) {
        s3Service.deleteFile(fileKey);
        return ResponseEntity.noContent().build();
    }
}
