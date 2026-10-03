package com.vista.pdg.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vista.pdg.auth.dto.*;
import com.vista.pdg.auth.repository.*;
import com.vista.pdg.auth.service.*;
import com.vista.pdg.exception.*;
import com.vista.pdg.testsupport.MutableClockConfig;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

class EmailVerificationTest extends IntegrationTestSupport {
  @Autowired EmailVerificationRepository codes;
  @Autowired UserRepository users;
  @Autowired AuthService auth;
  @Autowired VerificationQuotaService quota;
  @Autowired VerificationQuotaRepository quotaRows;

  @AfterEach
  void reset() {
    MutableClockConfig.CLOCK.reset();
    verificationMail.fail = false;
    verificationMail.configured = true;
    ReflectionTestUtils.setField(emailVerification, "cooldown", Duration.ZERO);
    ReflectionTestUtils.setField(emailVerification, "emailHourlyLimit", 10000);
    ReflectionTestUtils.setField(quota, "hourlyLimit", 10000);
  }

  private RegistrationIntent intent(String email) {
    return new RegistrationIntent("Prueba", email, "clave12345", "clave12345", "CEDI-G1");
  }

  private RegisterRequest payload(String email, VerificationResponse receipt, String code) {
    return new RegisterRequest(
        "Prueba", email, "clave12345", "clave12345", "CEDI-G1", receipt.verificationId(), code);
  }

  @Test
  void solicitaCodigoSinCrearCuentaNiSesion() throws Exception {
    String email = uniqueEmail("correo");
    var result =
        mockMvc
            .perform(
                post("/api/auth/registration-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(intent(email))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.verificationId").isNotEmpty())
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andReturn();
    assertThat(users.findByEmail(email)).isEmpty();
    assertThat(verificationMail.code(email)).matches("[0-9]{6}");
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain(verificationMail.code(email));
    assertThat(codes.findById(email).orElseThrow().getCodeHash())
        .hasSize(64)
        .doesNotContain(verificationMail.code(email));
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(email, "clave12345"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void noPermiteRegistroDirectoSinCodigo() throws Exception {
    String email = uniqueEmail("sin.codigo");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(intent(email))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.verificationId").exists());
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void validaCodigoCreaCuentaVerificadaYNoLoReutiliza() {
    String email = uniqueEmail("verificada");
    var receipt = auth.requestVerification(intent(email));
    var req = payload(email, receipt, verificationMail.code(email));
    var session = auth.register(req);
    assertThat(session.accessToken()).isNotBlank();
    assertThat(users.findByEmail(email).orElseThrow().getEmailVerifiedAt()).isNotNull();
    assertThatThrownBy(
            () ->
                emailVerification.consume(email, receipt.verificationId(), req.verificationCode()))
        .isInstanceOf(VerificationException.class);
  }

  @Test
  void intentosFallidosPersistenYBloqueanAunqueLuegoLlegueCodigoCorrecto() {
    String email = uniqueEmail("intentos");
    var receipt = auth.requestVerification(intent(email));
    String good = verificationMail.code(email);
    String wrong = good.equals("000000") ? "000001" : "000000";
    for (int i = 0; i < 5; i++)
      assertThatThrownBy(() -> auth.register(payload(email, receipt, wrong)))
          .isInstanceOf(VerificationException.class);
    assertThat(codes.findById(email).orElseThrow().getAttempts()).isEqualTo(5);
    assertThatThrownBy(() -> auth.register(payload(email, receipt, good)))
        .isInstanceOf(VerificationException.class)
        .hasMessageContaining("límite");
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void venceYReenvioInvalidaCodigoAnterior() {
    String email = uniqueEmail("vence");
    var old = auth.requestVerification(intent(email));
    String oldCode = verificationMail.code(email);
    MutableClockConfig.CLOCK.advance(Duration.ofMinutes(11));
    assertThatThrownBy(() -> auth.register(payload(email, old, oldCode)))
        .isInstanceOf(VerificationException.class)
        .hasMessageContaining("venció");
    var next = auth.requestVerification(intent(email));
    assertThatThrownBy(() -> auth.register(payload(email, old, oldCode)))
        .isInstanceOf(VerificationException.class);
    assertThat(auth.register(payload(email, next, verificationMail.code(email))).email())
        .isEqualTo(email);
  }

  @Test
  void codigoNoSirveParaOtroCorreoYNoCambiaRoles() {
    String email = uniqueEmail("vinculo");
    var receipt = auth.requestVerification(intent(email));
    assertThatThrownBy(
            () ->
                auth.register(payload(uniqueEmail("otro"), receipt, verificationMail.code(email))))
        .isInstanceOf(VerificationException.class);
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void correoNoInstitucionalNuncaSeEnvia() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/registration-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(intent("external@example.invalid"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.email").exists());
    assertThat(verificationMail.code("external@example.invalid")).isNull();
  }

  @Test
  void cooldownYLimiteDeCorreoDevuelvenRetryAfter() throws Exception {
    ReflectionTestUtils.setField(emailVerification, "cooldown", Duration.ofMinutes(1));
    ReflectionTestUtils.setField(emailVerification, "emailHourlyLimit", 1);
    String email = uniqueEmail("cooldown");
    auth.requestVerification(intent(email));
    mockMvc
        .perform(
            post("/api/auth/registration-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(intent(email))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
    MutableClockConfig.CLOCK.advance(Duration.ofMinutes(2));
    assertThatThrownBy(() -> auth.requestVerification(intent(email)))
        .isInstanceOf(VerificationRateException.class);
  }

  @Test
  void falloDeSmtpNoCreaCuentaNiReemplazaCodigoAnterior() throws Exception {
    String email = uniqueEmail("smtp");
    var old = auth.requestVerification(intent(email));
    String code = verificationMail.code(email);
    verificationMail.fail = true;
    mockMvc
        .perform(
            post("/api/auth/registration-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(intent(email))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("VERIFICATION_MAIL_UNAVAILABLE"));
    assertThat(codes.findById(email).orElseThrow().getRequestId()).isEqualTo(old.verificationId());
    assertThat(users.findByEmail(email)).isEmpty();
    verificationMail.fail = false;
    assertThat(auth.register(payload(email, old, code)).email()).isEqualTo(email);
  }

  @Test
  void smtpSinConfigurarFallaCerrado() throws Exception {
    verificationMail.configured = false;
    String email = uniqueEmail("desactivado");
    mockMvc
        .perform(
            post("/api/auth/registration-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(intent(email))))
        .andExpect(status().isServiceUnavailable());
    assertThat(codes.findById(email)).isEmpty();
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void presupuestoGlobalEsDurableInclusoAlFallarSmtp() {
    quotaRows.deleteAll();
    ReflectionTestUtils.setField(quota, "hourlyLimit", 1);
    verificationMail.fail = true;
    assertThatThrownBy(() -> auth.requestVerification(intent(uniqueEmail("limite1"))))
        .isInstanceOf(VerificationMailException.class);
    verificationMail.fail = false;
    assertThatThrownBy(() -> auth.requestVerification(intent(uniqueEmail("limite2"))))
        .isInstanceOf(VerificationRateException.class);
  }

  @Test
  void dosConfirmacionesConcurrentesCreanSoloUnaCuenta() throws Exception {
    String email = uniqueEmail("concurrente");
    var receipt = auth.requestVerification(intent(email));
    var req = payload(email, receipt, verificationMail.code(email));
    try (var pool = Executors.newFixedThreadPool(2)) {
      CountDownLatch start = new CountDownLatch(1);
      Callable<Boolean> attempt =
          () -> {
            start.await();
            try {
              auth.register(req);
              return true;
            } catch (VerificationException | EmailAlreadyUsedException ex) {
              return false;
            }
          };
      var a = pool.submit(attempt);
      var b = pool.submit(attempt);
      start.countDown();
      assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(users.findAll().stream().filter(u -> u.getEmail().equals(email))).hasSize(1);
  }
}
