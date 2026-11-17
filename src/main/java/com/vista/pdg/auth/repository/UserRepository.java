package com.vista.pdg.auth.repository;

import com.vista.pdg.auth.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByRoles_Id(Long roleId);

  /** Matriculados de un curso (HU-34): cuentas activas con ese curso y el rol indicado. */
  List<User> findByCourse_IdAndRoles_NameAndEnabledTrueOrderByDisplayNameAsc(
      Long courseId, String roleName);
}
