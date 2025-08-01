/*
 * Copyright 2004-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.config.annotation.web.builders;

import java.io.IOException;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.PasswordEncodedUser;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.config.Customizer.withDefaults;

/**
 * @author Rob Winch
 */
public class WebSecurityTests {

	AnnotationConfigWebApplicationContext context;

	MockHttpServletRequest request;

	MockHttpServletResponse response;

	MockFilterChain chain;

	@Autowired
	Filter springSecurityFilterChain;

	@BeforeEach
	public void setup() {
		this.request = new MockHttpServletRequest("GET", "");
		this.request.setMethod("GET");
		this.response = new MockHttpServletResponse();
		this.chain = new MockFilterChain();
	}

	@AfterEach
	public void cleanup() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void requestRejectedHandlerInvoked() throws ServletException, IOException {
		loadConfig(DefaultConfig.class);
		this.request.setServletPath("/spring");
		this.request.setRequestURI("/spring/\u0019path");
		this.springSecurityFilterChain.doFilter(this.request, this.response, this.chain);
		assertThat(this.response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
	}

	@Test
	public void customRequestRejectedHandlerInvoked() throws ServletException, IOException {
		loadConfig(RequestRejectedHandlerConfig.class);
		this.request.setServletPath("/spring");
		this.request.setRequestURI("/spring/\u0019path");
		this.springSecurityFilterChain.doFilter(this.request, this.response, this.chain);
		assertThat(this.response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
	}

	// gh-12548
	@Test
	public void requestRejectedHandlerInvokedWhenOperationalObservationRegistry() throws ServletException, IOException {
		loadConfig(ObservationRegistryConfig.class);
		this.request.setServletPath("/spring");
		this.request.setRequestURI("/spring/\u0019path");
		this.springSecurityFilterChain.doFilter(this.request, this.response, this.chain);
		assertThat(this.response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
	}

	public void loadConfig(Class<?>... configs) {
		this.context = new AnnotationConfigWebApplicationContext();
		this.context.register(configs);
		this.context.setServletContext(new MockServletContext());
		this.context.refresh();
		this.context.getAutowireCapableBeanFactory().autowireBean(this);
	}

	@EnableWebSecurity
	static class DefaultConfig {

	}

	@EnableWebSecurity
	@Configuration
	@EnableWebMvc
	static class MvcMatcherConfig {

		@Bean
		WebSecurityCustomizer webSecurityCustomizer(PathPatternRequestMatcher.Builder builder) {
			return (web) -> web.ignoring().requestMatchers(builder.matcher("/path"));
		}

		@Bean
		SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.httpBasic(withDefaults())
				.authorizeHttpRequests((requests) -> requests
					.anyRequest().denyAll());
			// @formatter:on
			return http.build();
		}

		@Bean
		UserDetailsService userDetailsService() {
			return new InMemoryUserDetailsManager(PasswordEncodedUser.user());
		}

		@RestController
		static class PathController {

			@RequestMapping("/path")
			String path() {
				return "path";
			}

		}

	}

	@EnableWebSecurity
	@Configuration
	@EnableWebMvc
	static class MvcMatcherServletPathConfig {

		@Bean
		WebSecurityCustomizer webSecurityCustomizer(PathPatternRequestMatcher.Builder builder) {
			return (web) -> web.ignoring()
				.requestMatchers(builder.basePath("/spring").matcher("/path"))
				.requestMatchers("/notused");
		}

		@Bean
		SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.httpBasic(withDefaults())
				.authorizeHttpRequests((requests) -> requests
					.anyRequest().denyAll());
			// @formatter:on
			return http.build();
		}

		@Bean
		UserDetailsService userDetailsService() {
			return new InMemoryUserDetailsManager(PasswordEncodedUser.user());
		}

		@RestController
		static class PathController {

			@RequestMapping("/path")
			String path() {
				return "path";
			}

		}

	}

	@Configuration
	@EnableWebSecurity
	static class RequestRejectedHandlerConfig {

		@Bean
		WebSecurityCustomizer webSecurityCustomizer() {
			return (web) -> web
				.requestRejectedHandler(new HttpStatusRequestRejectedHandler(HttpStatus.BAD_REQUEST.value()));
		}

	}

	@Configuration
	@EnableWebSecurity
	static class ObservationRegistryConfig {

		@Bean
		ObservationRegistry observationRegistry() {
			ObservationRegistry observationRegistry = ObservationRegistry.create();
			observationRegistry.observationConfig().observationHandler(new ObservationTextPublisher());
			return observationRegistry;
		}

	}

}
