package com.vista.pdg.auth.service;

import com.vista.pdg.exception.VerificationMailException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpVerificationMailer implements VerificationMailer {
  private final ObjectProvider<JavaMailSender> sender;
  private final boolean enabled;
  private final String from;

  @Value("${spring.profiles.active:dev}")
  private String profiles;

  @Value("${spring.mail.properties.mail.smtp.starttls.required:true}")
  private boolean tls;

  @jakarta.annotation.PostConstruct
  void requireTls() {
    if (enabled && java.util.Arrays.asList(profiles.split(",")).contains("prod") && !tls)
      throw new IllegalStateException("SMTP en producción exige TLS");
  }

  public SmtpVerificationMailer(
      ObjectProvider<JavaMailSender> sender,
      @Value("${auth.verification.mail-enabled:false}") boolean enabled,
      @Value("${auth.verification.from:}") String from) {
    this.sender = sender;
    this.enabled = enabled;
    this.from = from;
  }

  public void checkConfigured() {
    if (!enabled || from.isBlank() || sender.getIfAvailable() == null)
      throw new VerificationMailException();
  }

  public void send(String email, String code, long minutes) {
    checkConfigured();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject("VISTA · Verifica tu correo institucional");
    message.setText(
        "Tu código de verificación de VISTA es: "
            + code
            + "\n\nVence en "
            + minutes
            + " minutos y solo sirve para crear tu cuenta. No lo compartas.\nSi no solicitaste este registro, puedes ignorar este correo. Tu cuenta no se ha creado.");
    try {
      sender.getObject().send(message);
    } catch (org.springframework.mail.MailException ex) {
      // No incluir el cuerpo, destinatario, credenciales ni detalles SMTP en logs/respuestas.
      throw new VerificationMailException();
    }
  }
}
