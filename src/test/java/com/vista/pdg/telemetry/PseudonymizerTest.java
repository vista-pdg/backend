package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vista.pdg.telemetry.service.Pseudonymizer;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unitaria, sin Spring: fija las propiedades del seudónimo de las que depende CA-5. */
class PseudonymizerTest {

  private static final String KEY_A = "clave-de-pruebas-a-con-al-menos-32-caracteres";
  private static final String KEY_B = "clave-de-pruebas-b-con-al-menos-32-caracteres";

  @Test
  @DisplayName("es determinista: la misma cuenta da siempre el mismo seudónimo")
  void determinista() {
    Pseudonymizer p = new Pseudonymizer(KEY_A);
    assertThat(p.pseudonymFor(42L)).isEqualTo(p.pseudonymFor(42L));
  }

  @Test
  @DisplayName("tiene 16 hexadecimales")
  void formato() {
    assertThat(new Pseudonymizer(KEY_A).pseudonymFor(1L)).hasSize(16).matches("[0-9a-f]{16}");
  }

  @Test
  @DisplayName("cuentas consecutivas no producen seudónimos relacionados ni repetidos")
  void sinColisionesEnRangoPequeno() {
    Pseudonymizer p = new Pseudonymizer(KEY_A);
    Set<String> seen = new HashSet<>();
    for (long id = 1; id <= 1000; id++) assertThat(seen.add(p.pseudonymFor(id))).isTrue();
  }

  /** Sin esto, un volcado de la tabla sería reversible probando ids del 1 en adelante. */
  @Test
  @DisplayName("depende de la clave: otra clave, otro seudónimo para la misma cuenta")
  void dependeDeLaClave() {
    assertThat(new Pseudonymizer(KEY_A).pseudonymFor(7L))
        .isNotEqualTo(new Pseudonymizer(KEY_B).pseudonymFor(7L));
  }

  @Test
  @DisplayName("no acepta claves cortas: una clave débil convierte el HMAC en un hash adivinable")
  void rechazaClaveCorta() {
    assertThatThrownBy(() -> new Pseudonymizer("corta"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32");
  }

  @Test
  @DisplayName("el seudónimo no contiene el identificador original")
  void noContieneElId() {
    assertThat(new Pseudonymizer(KEY_A).pseudonymFor(123456L)).doesNotContain("123456");
  }
}
