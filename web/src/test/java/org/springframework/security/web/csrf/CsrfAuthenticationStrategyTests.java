/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.security.web.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * @author Rob Winch
 *
 */
@ExtendWith(MockitoExtension.class)
public class CsrfAuthenticationStrategyTests {

	@Mock
	private CsrfTokenRepository csrfTokenRepository;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private CsrfAuthenticationStrategy strategy;

	private CsrfToken existingToken;

	private CsrfToken generatedToken;

	@BeforeEach
	public void setup() {
		this.response = new MockHttpServletResponse();
		this.request = new MockHttpServletRequest();
		this.request.setAttribute(HttpServletResponse.class.getName(), this.response);
		this.strategy = new CsrfAuthenticationStrategy(this.csrfTokenRepository);
		this.existingToken = new DefaultCsrfToken("_csrf", "_csrf", "1");
		this.generatedToken = new DefaultCsrfToken("_csrf", "_csrf", "2");
	}

	@Test
	public void constructorNullCsrfTokenRepository() {
		assertThatIllegalArgumentException().isThrownBy(() -> new CsrfAuthenticationStrategy(null));
	}

	@Test
	public void setRequestHandlerWhenNullThenIllegalStateException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.strategy.setRequestHandler(null))
				.withMessage("requestHandler cannot be null");
	}

	@Test
	public void onAuthenticationWhenCustomRequestHandlerThenUsed() {
		CsrfTokenRequestHandler requestHandler = mock(CsrfTokenRequestHandler.class);
		this.strategy.setRequestHandler(requestHandler);
		this.strategy.onAuthentication(new TestingAuthenticationToken("user", "password", "ROLE_USER"), this.request,
				this.response);
		verify(requestHandler).handle(eq(this.request), eq(this.response));
		verifyNoMoreInteractions(requestHandler);
	}

	@Test
	public void logoutRemovesCsrfTokenAndSavesNew() {
		given(this.csrfTokenRepository.loadToken(this.request)).willReturn(null, this.existingToken);
		given(this.csrfTokenRepository.generateToken(this.request)).willReturn(this.generatedToken);
		this.strategy.onAuthentication(new TestingAuthenticationToken("user", "password", "ROLE_USER"), this.request,
				this.response);
		// SEC-2404, SEC-2832
		CsrfToken tokenInRequest = (CsrfToken) this.request.getAttribute(CsrfToken.class.getName());
		assertThat(tokenInRequest.getToken()).isSameAs(this.generatedToken.getToken());
		assertThat(tokenInRequest.getHeaderName()).isSameAs(this.generatedToken.getHeaderName());
		assertThat(tokenInRequest.getParameterName()).isSameAs(this.generatedToken.getParameterName());
		assertThat(this.request.getAttribute(this.generatedToken.getParameterName())).isSameAs(tokenInRequest);
		// verify after the test accesses the CsrfToken which causes the lazy save to
		// occur
		verify(this.csrfTokenRepository).saveToken(null, this.request, this.response);
		verify(this.csrfTokenRepository).saveToken(eq(this.generatedToken), any(HttpServletRequest.class),
				any(HttpServletResponse.class));
	}

	// SEC-2872
	@Test
	public void delaySavingCsrf() {
		this.strategy = new CsrfAuthenticationStrategy(new LazyCsrfTokenRepository(this.csrfTokenRepository));
		given(this.csrfTokenRepository.generateToken(this.request)).willReturn(this.generatedToken);
		this.strategy.onAuthentication(new TestingAuthenticationToken("user", "password", "ROLE_USER"), this.request,
				this.response);
		verify(this.csrfTokenRepository).saveToken(null, this.request, this.response);
		verify(this.csrfTokenRepository, never()).saveToken(eq(this.generatedToken), any(HttpServletRequest.class),
				any(HttpServletResponse.class));
		CsrfToken tokenInRequest = (CsrfToken) this.request.getAttribute(CsrfToken.class.getName());
		tokenInRequest.getToken();
		verify(this.csrfTokenRepository).saveToken(eq(this.generatedToken), any(HttpServletRequest.class),
				any(HttpServletResponse.class));
	}

	@Test
	public void logoutWhenNoCsrfToken() {
		this.strategy.onAuthentication(new TestingAuthenticationToken("user", "password", "ROLE_USER"), this.request,
				this.response);
		verify(this.csrfTokenRepository).saveToken(isNull(), any(HttpServletRequest.class),
				any(HttpServletResponse.class));
	}

}