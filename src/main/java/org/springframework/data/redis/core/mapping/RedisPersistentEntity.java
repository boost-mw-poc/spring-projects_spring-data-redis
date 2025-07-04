/*
 * Copyright 2015-2025 the original author or authors.
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
package org.springframework.data.redis.core.mapping;

import org.jspecify.annotations.Nullable;
import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentEntity;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.redis.core.TimeToLiveAccessor;

/**
 * Redis specific {@link PersistentEntity}.
 *
 * @author Christoph Strobl
 * @param <T>
 * @since 1.7
 */
public interface RedisPersistentEntity<T> extends KeyValuePersistentEntity<T, RedisPersistentProperty> {

	/**
	 * Get the {@link TimeToLiveAccessor} associated with the entity.
	 *
	 * @return never {@literal null}.
	 */
	TimeToLiveAccessor getTimeToLiveAccessor();

	/**
	 * @return {@literal true} when a property is annotated with {@link org.springframework.data.redis.core.TimeToLive}.
	 * @since 3.4
	 */
	default boolean hasExplicitTimeToLiveProperty() {
		return getExplicitTimeToLiveProperty() != null;
	}

	/**
	 * Get the {@link PersistentProperty} that is annotated with {@link org.springframework.data.redis.core.TimeToLive}.
	 *
	 * @return can be {@literal null}.
	 * @since 1.8
	 */
	@Nullable
	RedisPersistentProperty getExplicitTimeToLiveProperty();

	/**
	 * @return {@literal true} if the entity could potentially expire.
	 * @since 2.3
	 */
	default boolean isExpiring() {
		return getTimeToLiveAccessor().isExpiringEntity(getType());
	}

}
