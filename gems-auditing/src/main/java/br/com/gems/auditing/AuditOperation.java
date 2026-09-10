package br.com.gems.auditing;

/**
 * A operação que originou o registro na trilha.
 * <p>
 * {@link #DELETE} cobre também a exclusão lógica: uma entidade com {@code @SQLDelete} sofre um
 * {@code update} no banco, mas o que aconteceu do ponto de vista de quem audita é uma exclusão, e é
 * assim que a trilha precisa registrá-la para que a consulta faça sentido.
 * </p>
 */
public enum AuditOperation {
    INSERT,
    UPDATE,
    DELETE
}
