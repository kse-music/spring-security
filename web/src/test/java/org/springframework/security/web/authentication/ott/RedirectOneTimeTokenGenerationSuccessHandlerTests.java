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

package org.springframework.security.web.authentication.ott;

import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link RedirectOneTimeTokenGenerationSuccessHandler}
 *
 * @author Marcus da Coregio
 */
class RedirectOneTimeTokenGenerationSuccessHandlerTests {

	@Test
	void handleThenRedirectToDefaultLocation() throws IOException {
		RedirectOneTimeTokenGenerationSuccessHandler handler = new RedirectOneTimeTokenGenerationSuccessHandler(
				"/login/ott");
		MockHttpServletResponse response = new MockHttpServletResponse();
		handler.handle(new MockHttpServletRequest(), response, new DefaultOneTimeToken("token", "user", Instant.now()));
		assertThat(response.getRedirectedUrl()).isEqualTo("/login/ott");
	}

	@Test
	void handleWhenUrlChangedThenRedirectToUrl() throws IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();
		RedirectOneTimeTokenGenerationSuccessHandler handler = new RedirectOneTimeTokenGenerationSuccessHandler(
				"/redirected");
		handler.handle(new MockHttpServletRequest(), response, new DefaultOneTimeToken("token", "user", Instant.now()));
		assertThat(response.getRedirectedUrl()).isEqualTo("/redirected");
	}

	@Test
	void setRedirectUrlWhenNullOrEmptyThenException() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RedirectOneTimeTokenGenerationSuccessHandler(null))
			.withMessage("redirectUrl cannot be empty or null");
		assertThatIllegalArgumentException().isThrownBy(() -> new RedirectOneTimeTokenGenerationSuccessHandler(""))
			.withMessage("redirectUrl cannot be empty or null");
	}

}
