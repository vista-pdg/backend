package com.vista.pdg.auth.repository;

import com.vista.pdg.auth.entity.RefreshToken;
import com.vista.pdg.auth.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByFamilyId(String familyId);

  /**
   * Revoca de una sola vez todos los eslabones vivos de una familia. Se usa en el logout y, sobre
   * todo, cuando se detecta el reuso de un token: en ese momento no se sabe si el legítimo es quien
   * acaba de llamar o el atacante, así que se corta la cadena completa.
   */
  @Modifying
  @Query(
      "update RefreshToken r set r.revokedAt = :now "
          + "where r.familyId = :familyId and r.revokedAt is null")
  int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

  /**
   * Limpieza oportunista para que la tabla no crezca sin límite.
   *
   * <p>Borra únicamente lo <b>expirado</b>. Un token ya rotado pero todavía dentro de su ventana de
   * validez tiene que seguir en la tabla: es justo la fila que permite reconocer un reuso. Si se
   * borrara, presentarlo por segunda vez daría «token desconocido» en lugar de revocar la familia,
   * y la detección dejaría de funcionar en silencio.
   */
  @Modifying
  @Query("delete from RefreshToken r where r.user = :user and r.expiresAt < :now")
  int deleteExpiredTokens(@Param("user") User user, @Param("now") Instant now);
}
