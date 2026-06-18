package br.com.gems.aws.service;

import br.com.gems.aws.dto.GenerateUploadUrlRequestDTO;
import br.com.gems.aws.dto.PresignedUrlResponseDTO;
import br.com.gems.exception.exception.BusinessException;
import br.com.gems.utils.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Serviço responsável por orquestrar a comunicação com a AWS S3.
 * <p>
 * Contém a lógica de negócio para a geração de URLs pré-assinadas (Presigned URLs)
 * usando a SDK v2 da AWS, além de recursos para verificação de existência e exclusão de objetos.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiration-minutes:15}")
    private long expirationMinutes;

    /**
     * Gera uma URL pré-assinada para que o cliente (frontend) possa fazer o upload (PUT) 
     * diretamente para o S3 de forma segura e com expiração definida.
     *
     * @param request A solicitação contendo detalhes como nome do arquivo, tipo de conteúdo e diretório opcional.
     * @return O DTO {@link PresignedUrlResponseDTO} com a URL e a respectiva chave de arquivo gerada no S3.
     * @throws BusinessException Caso o nome do arquivo seja nulo ou ocorra algum erro na comunicação com a AWS.
     */
    public PresignedUrlResponseDTO generatePresignedUploadUrl(GenerateUploadUrlRequestDTO request) {
        if (ObjectUtil.isNullOrEmpty(request.getFileName())) {
            throw new BusinessException("O nome do arquivo é obrigatório para gerar a URL de upload.");
        }

        // Gera uma chave única para evitar sobrescrita
        String uniqueFileName = UUID.randomUUID().toString() + "_" + request.getFileName();
        String fileKey = ObjectUtil.isNotNullAndNotEmpty(request.getDirectory())
                ? request.getDirectory() + "/" + uniqueFileName
                : uniqueFileName;

        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(request.getContentType())
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .putObjectRequest(objectRequest)
                    .build();

            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            return PresignedUrlResponseDTO.builder()
                    .url(presignedUrl)
                    .fileKey(fileKey)
                    .build();

        } catch (Exception e) {
            log.error("Erro ao gerar URL presignada para upload no S3", e);
            throw new BusinessException("Não foi possível gerar a URL de upload.");
        }
    }

    /**
     * Gera uma URL pré-assinada para que o cliente possa fazer o download (GET) 
     * de um arquivo privado armazenado no bucket.
     *
     * @param fileKey A chave ou o caminho completo do objeto dentro do bucket S3.
     * @return O DTO {@link PresignedUrlResponseDTO} contendo a URL temporária para acesso ao arquivo.
     * @throws BusinessException Se a fileKey for nula ou caso haja falha ao solicitar a assinatura da URL.
     */
    public PresignedUrlResponseDTO generatePresignedDownloadUrl(String fileKey) {
        if (ObjectUtil.isNullOrEmpty(fileKey)) {
            throw new BusinessException("A chave do arquivo (fileKey) é obrigatória para gerar a URL de download.");
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            String presignedUrl = s3Presigner.presignGetObject(getObjectPresignRequest).url().toString();

            return PresignedUrlResponseDTO.builder()
                    .url(presignedUrl)
                    .fileKey(fileKey)
                    .build();

        } catch (Exception e) {
            log.error("Erro ao gerar URL presignada para download no S3. Chave: " + fileKey, e);
            throw new BusinessException("Não foi possível gerar a URL de download.");
        }
    }

    /**
     * Remove um arquivo de forma permanente do S3.
     *
     * @param fileKey A chave ou o caminho completo do objeto a ser excluído.
     * @throws BusinessException Se a fileKey for nula ou não for possível se comunicar com o S3.
     */
    public void deleteFile(String fileKey) {
        if (ObjectUtil.isNullOrEmpty(fileKey)) {
            throw new BusinessException("A chave do arquivo (fileKey) é obrigatória para deletar o arquivo.");
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Arquivo deletado com sucesso do S3: {}", fileKey);

        } catch (Exception e) {
            log.error("Erro ao deletar arquivo do S3. Chave: " + fileKey, e);
            throw new BusinessException("Não foi possível deletar o arquivo.");
        }
    }

    /**
     * Verifica de forma eficiente (usando requisição HEAD) se um determinado objeto existe no bucket S3.
     *
     * @param fileKey A chave completa do arquivo para ser verificada.
     * @return {@code true} caso o arquivo exista no S3, {@code false} caso contrário ou se ocorrer algum erro.
     */
    public boolean fileExists(String fileKey) {
        if (ObjectUtil.isNullOrEmpty(fileKey)) {
            return false;
        }

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Erro ao verificar existência de arquivo no S3: {}", fileKey, e);
            return false;
        }
    }
}
