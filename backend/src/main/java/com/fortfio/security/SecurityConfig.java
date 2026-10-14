package com.fortfio.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fortfio.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class SecurityConfig {

	static final String ROLE_CLAIM = "role";
	private static final int MIN_SECRET_BYTES = 32;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/password/*",
								"/api/auth/google", "/api/events", "/api/trial")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/workflows", "/api/portfolio", "/api/auth/options",
								"/api/trial/**").permitAll()
						.requestMatchers("/files/**", "/actuator/health", "/error").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())));
		return http.build();
	}

	private static JwtAuthenticationConverter roleConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(properties.cors().allowedOrigins());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}

	@Bean
	SecretKey jwtSigningKey(AppProperties properties) {
		String secret = properties.auth().jwtSecret();
		if (secret == null || secret.isBlank()) {
			log.warn("JWT_SECRET is not set: using a random key, logins will be lost on restart");
			byte[] random = new byte[MIN_SECRET_BYTES];
			new SecureRandom().nextBytes(random);
			return new SecretKeySpec(random, "HmacSHA256");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " characters");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
