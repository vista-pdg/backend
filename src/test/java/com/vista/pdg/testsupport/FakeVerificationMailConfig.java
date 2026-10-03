package com.vista.pdg.testsupport;

import com.vista.pdg.auth.service.VerificationMailer;
import com.vista.pdg.exception.VerificationMailException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FakeVerificationMailConfig {
  @Bean
  @Primary
  public CapturingMailer verificationTestMailer() {
    return new CapturingMailer();
  }

  public static class CapturingMailer implements VerificationMailer {
    private final ConcurrentHashMap<String, String> codes = new ConcurrentHashMap<>();
    public volatile boolean fail = false;
    public volatile boolean configured = true;

    public void checkConfigured() {
      if (!configured) throw new VerificationMailException();
    }

    public void send(String email, String code, long minutes) {
      checkConfigured();
      if (fail) throw new VerificationMailException();
      codes.put(email, code);
    }

    public String code(String email) {
      return codes.get(email.toLowerCase(java.util.Locale.ROOT).strip());
    }
  }
}
