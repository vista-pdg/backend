package com.vista.pdg.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // El contenedor reenvía los errores a /error, y ese reenvío vuelve a pasar por
                    // la cadena sin autenticación. Sin permitirlo, la respuesta del reenvío pisa a
                    // la original: un 403 por rol insuficiente salía como 401. MockMvc no ejecuta
                    // ese despacho, así que sólo se ve contra un servidor real.
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    // El propio token de refresco autentica estas llamadas, así que no exigen
                    // un token de acceso: en /refresh y /logout lo normal es que ya haya expirado.
                    .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout")
                    .permitAll()
                    // El selector de curso del registro se consulta antes de que exista sesión.
                    .requestMatchers(HttpMethod.GET, "/api/courses")
                    .permitAll()
                    // HU-16 CA-4: sólo el docente ve agregados; el estudiante recibe 403.
                    .requestMatchers("/api/analytics/**")
                    .hasRole("TEACHER")
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    // HU-16 CA-3: el asistente ya no es anónimo. Cae en
                    // anyRequest().authenticated().
                    .anyRequest()
                    .authenticated())
        // 401 cuando no hay autenticación, 403 cuando la hay pero el rol no alcanza. El cliente
        // necesita distinguirlas: la primera la resuelve refrescando el token, la segunda no.
        .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
