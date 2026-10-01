package app.psst.chat.config;

import app.psst.chat.service.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);


    /**
     * simple authentication using OTT, something that doesnt need a password
     * could use email as the username instead of plaintext because thers gonna be a limitation on how many
     * users this app can handle
     *
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login/ott", "/ott/generate").permitAll());
        http.with(VaadinSecurityConfigurer.vaadin(), config -> {});
        http.oneTimeTokenLogin(ott -> ott
                .tokenGenerationSuccessHandler((request, response, token) -> {
                    String username;
                    try {
                        username = UserService.validateUsername(token.getUsername());
                    } catch (IllegalArgumentException invalid) {
                        response.sendError(400, invalid.getMessage());
                        return;
                    }
                    log.info("OTT for {}: {}", username, token.getTokenValue());
                    response.sendRedirect(request.getContextPath() + "/login/ott");
                })
                .defaultSuccessUrl("/chat", true)
                .permitAll());
        http.logout(logout -> logout.logoutSuccessHandler((request, response, authentication) -> {
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.getPage().setLocation("/login?logout");
            } else {
                response.sendRedirect(request.getContextPath() + "/login?logout");
            }
        }));
        return http.build();
    }
}
