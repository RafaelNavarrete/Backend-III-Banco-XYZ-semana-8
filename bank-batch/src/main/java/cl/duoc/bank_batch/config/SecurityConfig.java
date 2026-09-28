package cl.duoc.bank_batch.config;

import cl.duoc.bank_batch.security.JwtAuthenticationFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // -------------------------------------------------
    // USUARIOS Y ROLES POR CANAL
    // -------------------------------------------------

    @Bean
    public UserDetailsService userDetailsService() {

        UserDetails usuarioWeb = User.withUsername("web")
                .password("{noop}web123")
                .roles("WEB")
                .build();

        UserDetails usuarioMovil = User.withUsername("movil")
                .password("{noop}movil123")
                .roles("MOVIL")
                .build();

        UserDetails usuarioCajero = User.withUsername("cajero")
                .password("{noop}cajero123")
                .roles("CAJERO")
                .build();

        return new InMemoryUserDetailsManager(
                usuarioWeb,
                usuarioMovil,
                usuarioCajero
        );
    }

    // -------------------------------------------------
    // AUTHENTICATION MANAGER
    // -------------------------------------------------

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }

    // -------------------------------------------------
    // REGLAS DE SEGURIDAD
    // -------------------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth

                        // RUTAS PUBLICAS
                        .requestMatchers(
                                "/auth/login",
                                "/api/debug/**",
                                "/api/kafka/**"
                        )
                        .permitAll()

                        // BFF WEB
                        .requestMatchers("/api/bff/web/**")
                        .hasRole("WEB")

                        // BFF MOVIL
                        .requestMatchers("/api/bff/movil/**")
                        .hasRole("MOVIL")

                        // BFF CAJERO
                        .requestMatchers("/api/bff/cajero/**")
                        .hasRole("CAJERO")

                        // CUALQUIER OTRA RUTA REQUIERE AUTENTICACION
                        .anyRequest()
                        .authenticated()
                )

                // JWT SE PROCESA ANTES DE LA AUTENTICACION ESTANDAR
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}