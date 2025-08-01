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

package org.springframework.security.crypto.scrypt;

import java.security.MessageDigest;
import java.util.Base64;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.crypto.generators.SCrypt;

import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.AbstractValidatingPasswordEncoder;

/**
 * <p>
 * Implementation of PasswordEncoder that uses the SCrypt hashing function. Clients can
 * optionally supply a cpu cost parameter, a memory cost parameter and a parallelization
 * parameter.
 * </p>
 *
 * <p>
 * A few <a href=
 * "http://bouncy-castle.1462172.n4.nabble.com/Java-Bouncy-Castle-scrypt-implementation-td4656832.html">
 * warnings</a>:
 * </p>
 *
 * <ul>
 * <li>The currently implementation uses Bouncy castle which does not exploit
 * parallelism/optimizations that password crackers will, so there is an unnecessary
 * asymmetry between attacker and defender.</li>
 * <li>Scrypt is based on Salsa20 which performs poorly in Java (on par with AES) but
 * performs awesome (~4-5x faster) on SIMD capable platforms</li>
 * <li>While there are some that would disagree, consider reading -
 * <a href="https://blog.ircmaxell.com/2014/03/why-i-dont-recommend-scrypt.html"> Why I
 * Don't Recommend Scrypt</a> (for password storage)</li>
 * </ul>
 *
 * @author Shazin Sadakath
 * @author Rob Winch
 *
 */
public class SCryptPasswordEncoder extends AbstractValidatingPasswordEncoder {

	private static final int DEFAULT_CPU_COST = 65536;

	private static final int DEFAULT_MEMORY_COST = 8;

	private static final int DEFAULT_PARALLELISM = 1;

	private static final int DEFAULT_KEY_LENGTH = 32;

	private static final int DEFAULT_SALT_LENGTH = 16;

	private final Log logger = LogFactory.getLog(getClass());

	private final int cpuCost;

	private final int memoryCost;

	private final int parallelization;

	private final int keyLength;

	private final BytesKeyGenerator saltGenerator;

	/**
	 * Constructs a SCrypt password encoder with the provided parameters.
	 * @param cpuCost cpu cost of the algorithm (as defined in scrypt this is N). must be
	 * power of 2 greater than 1. Default is currently 65,536 or 2^16)
	 * @param memoryCost memory cost of the algorithm (as defined in scrypt this is r)
	 * Default is currently 8.
	 * @param parallelization the parallelization of the algorithm (as defined in scrypt
	 * this is p) Default is currently 1. Note that the implementation does not currently
	 * take advantage of parallelization.
	 * @param keyLength key length for the algorithm (as defined in scrypt this is dkLen).
	 * The default is currently 32.
	 * @param saltLength salt length (as defined in scrypt this is the length of S). The
	 * default is currently 16.
	 */
	public SCryptPasswordEncoder(int cpuCost, int memoryCost, int parallelization, int keyLength, int saltLength) {
		if (cpuCost <= 1) {
			throw new IllegalArgumentException("Cpu cost parameter must be > 1.");
		}
		if (memoryCost == 1 && cpuCost > 65536) {
			throw new IllegalArgumentException("Cpu cost parameter must be > 1 and < 65536.");
		}
		if (memoryCost < 1) {
			throw new IllegalArgumentException("Memory cost must be >= 1.");
		}
		int maxParallel = Integer.MAX_VALUE / (128 * memoryCost * 8);
		if (parallelization < 1 || parallelization > maxParallel) {
			throw new IllegalArgumentException("Parallelisation parameter p must be >= 1 and <= " + maxParallel
					+ " (based on block size r of " + memoryCost + ")");
		}
		if (keyLength < 1 || keyLength > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Key length must be >= 1 and <= " + Integer.MAX_VALUE);
		}
		if (saltLength < 1 || saltLength > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Salt length must be >= 1 and <= " + Integer.MAX_VALUE);
		}
		this.cpuCost = cpuCost;
		this.memoryCost = memoryCost;
		this.parallelization = parallelization;
		this.keyLength = keyLength;
		this.saltGenerator = KeyGenerators.secureRandom(saltLength);
	}

	/**
	 * Constructs a SCrypt password encoder with cpu cost of 16,384, memory cost of 8,
	 * parallelization of 1, a key length of 32 and a salt length of 64 bytes.
	 * @return the {@link SCryptPasswordEncoder}
	 * @since 5.8
	 * @deprecated Use {@link #defaultsForSpringSecurity_v5_8()} instead
	 */
	@Deprecated
	public static SCryptPasswordEncoder defaultsForSpringSecurity_v4_1() {
		return new SCryptPasswordEncoder(16384, 8, 1, 32, 64);
	}

	/**
	 * Constructs a SCrypt password encoder with cpu cost of 65,536, memory cost of 8,
	 * parallelization of 1, a key length of 32 and a salt length of 16 bytes.
	 * @return the {@link SCryptPasswordEncoder}
	 * @since 5.8
	 */
	public static SCryptPasswordEncoder defaultsForSpringSecurity_v5_8() {
		return new SCryptPasswordEncoder(DEFAULT_CPU_COST, DEFAULT_MEMORY_COST, DEFAULT_PARALLELISM, DEFAULT_KEY_LENGTH,
				DEFAULT_SALT_LENGTH);
	}

	@Override
	protected String encodeNonNullPassword(String rawPassword) {
		return digest(rawPassword, this.saltGenerator.generateKey());
	}

	@Override
	protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
		return decodeAndCheckMatches(rawPassword, encodedPassword);
	}

	@Override
	protected boolean upgradeEncodingNonNull(String encodedPassword) {
		String[] parts = encodedPassword.split("\\$");
		if (parts.length != 4) {
			throw new IllegalArgumentException("Encoded password does not look like SCrypt: " + encodedPassword);
		}
		long params = Long.parseLong(parts[1], 16);
		int cpuCost = (int) Math.pow(2, params >> 16 & 0xffff);
		int memoryCost = (int) params >> 8 & 0xff;
		int parallelization = (int) params & 0xff;
		return cpuCost < this.cpuCost || memoryCost < this.memoryCost || parallelization < this.parallelization;
	}

	private boolean decodeAndCheckMatches(CharSequence rawPassword, String encodedPassword) {
		String[] parts = encodedPassword.split("\\$");
		if (parts.length != 4) {
			return false;
		}
		long params = Long.parseLong(parts[1], 16);
		byte[] salt = decodePart(parts[2]);
		byte[] derived = decodePart(parts[3]);
		int cpuCost = (int) Math.pow(2, params >> 16 & 0xffff);
		int memoryCost = (int) params >> 8 & 0xff;
		int parallelization = (int) params & 0xff;
		byte[] generated = SCrypt.generate(Utf8.encode(rawPassword), salt, cpuCost, memoryCost, parallelization,
				this.keyLength);
		return MessageDigest.isEqual(derived, generated);
	}

	private String digest(CharSequence rawPassword, byte[] salt) {
		byte[] derived = SCrypt.generate(Utf8.encode(rawPassword), salt, this.cpuCost, this.memoryCost,
				this.parallelization, this.keyLength);
		String params = Long.toString(
				((int) (Math.log(this.cpuCost) / Math.log(2)) << 16L) | this.memoryCost << 8 | this.parallelization,
				16);
		StringBuilder sb = new StringBuilder((salt.length + derived.length) * 2);
		sb.append("$").append(params).append('$');
		sb.append(encodePart(salt)).append('$');
		sb.append(encodePart(derived));
		return sb.toString();
	}

	private byte[] decodePart(String part) {
		return Base64.getDecoder().decode(Utf8.encode(part));
	}

	private String encodePart(byte[] part) {
		return Utf8.decode(Base64.getEncoder().encode(part));
	}

}
