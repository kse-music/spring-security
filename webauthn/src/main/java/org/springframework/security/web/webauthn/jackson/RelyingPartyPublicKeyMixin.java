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

package org.springframework.security.web.webauthn.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;

/**
 * Jackson mixin for {@link RelyingPartyPublicKey}
 *
 * @author Rob Winch
 * @since 6.4
 */
abstract class RelyingPartyPublicKeyMixin {

	RelyingPartyPublicKeyMixin(
			@JsonProperty("credential") PublicKeyCredential<AuthenticatorAttestationResponse> credential,
			@JsonProperty("label") String label) {
	}

}
