/*
 * Copyright 2022-2025 the original author or authors.
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
package org.springframework.data.redis.connection.zset;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

/**
 * Default implementation for {@link Tuple} interface.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author John Blum
 */
public class DefaultTuple implements Tuple {

	private static final Double ZERO = 0.0d;

	private final Double score;
	private final byte[] value;

	/**
	 * Constructs a new {@link DefaultTuple}.
	 *
	 * @param value {@code byte[]} of the member's raw value.
	 * @param score {@link Double score} of the raw value used in sorting.
	 */
	public DefaultTuple(byte[] value, Double score) {

		this.score = score;
		this.value = value;
	}

	public Double getScore() {
		return score;
	}

	public byte[] getValue() {
		return value;
	}

	public boolean equals(@Nullable Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof DefaultTuple other))
			return false;
		if (!score.equals(other.score))
			return false;
		if (!Arrays.equals(value, other.value))
			return false;
		return true;
	}

	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + score.hashCode();
		result = prime * result + Arrays.hashCode(value);
		return result;
	}

	public int compareTo(Double o) {
		Double d = score;
		Double a = (o == null ? Double.valueOf(0.0d) : o);
		return d.compareTo(a);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [score=").append(score);
		sb.append(", value=").append(value == null ? "null" : new String(value));
		sb.append(']');
		return sb.toString();
	}
}
