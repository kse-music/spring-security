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

package org.springframework.security.web.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class TextEscapeUtilsTests {

	/**
	 * &amp;, &lt;, &gt;, &#34;, &#39 and&#32;(space) escaping
	 */
	@Test
	public void charactersAreEscapedCorrectly() {
		assertThat(TextEscapeUtils.escapeEntities("& a<script>\"'")).isEqualTo("&amp;&#32;a&lt;script&gt;&#34;&#39;");
	}

	@Test
	public void nullOrEmptyStringIsHandled() {
		assertThat(TextEscapeUtils.escapeEntities("")).isEqualTo("");
		assertThat(TextEscapeUtils.escapeEntities(null)).isNull();
	}

	@Test
	public void invalidLowSurrogateIsDetected() {
		assertThatIllegalArgumentException().isThrownBy(() -> TextEscapeUtils.escapeEntities("abc\uDCCCdef"));
	}

	@Test
	public void missingLowSurrogateIsDetected() {
		assertThatIllegalArgumentException().isThrownBy(() -> TextEscapeUtils.escapeEntities("abc\uD888a"));
	}

	@Test
	public void highSurrogateAtEndOfStringIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> TextEscapeUtils.escapeEntities("abc\uD888"));
	}

	/**
	 * Delta char: &#66560;
	 */
	@Test
	public void validSurrogatePairIsAccepted() {
		assertThat(TextEscapeUtils.escapeEntities("abc\uD801\uDC00a")).isEqualTo("abc&#66560;a");
	}

	@Test
	public void undefinedSurrogatePairIsIgnored() {
		assertThat(TextEscapeUtils.escapeEntities("abc\uDBFF\uDFFFa")).isEqualTo("abca");
	}

}
