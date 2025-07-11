/*
 * Copyright 2017-2025 the original author or authors.
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
package org.springframework.data.redis.connection.lettuce;

import io.lettuce.core.ReadFrom;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.util.Assert;

/**
 * {@link LettuceConnectionProvider} and {@link RedisClientProvider} for Redis Cluster connections.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Bruce Cloud
 * @author John Blum
 * @since 2.0
 */
class ClusterConnectionProvider implements LettuceConnectionProvider, RedisClientProvider {

	private volatile boolean initialized;

	private final Lock lock = new ReentrantLock();

	private @Nullable final ReadFrom readFrom;

	private final RedisClusterClient client;

	private final RedisCodec<?, ?> codec;

	/**
	 * Create new {@link ClusterConnectionProvider}.
	 *
	 * @param client must not be {@literal null}.
	 * @param codec must not be {@literal null}.
	 */
	ClusterConnectionProvider(RedisClusterClient client, RedisCodec<?, ?> codec) {
		this(client, codec, null);
	}

	/**
	 * Create new {@link ClusterConnectionProvider}.
	 *
	 * @param client must not be {@literal null}.
	 * @param codec must not be {@literal null}.
	 * @param readFrom can be {@literal null}.
	 * @since 2.1
	 */
	ClusterConnectionProvider(RedisClusterClient client, RedisCodec<?, ?> codec, @Nullable ReadFrom readFrom) {

		Assert.notNull(client, "Client must not be null");
		Assert.notNull(codec, "Codec must not be null");

		this.client = client;
		this.codec = codec;
		this.readFrom = readFrom;
	}

	private Optional<ReadFrom> getReadFrom() {
		return Optional.ofNullable(this.readFrom);
	}

	@Override
	public <T extends StatefulConnection<?, ?>> CompletableFuture<T> getConnectionAsync(Class<T> connectionType) {

		if (!initialized) {

			// Partitions have to be initialized before asynchronous usage.
			// Needs to happen only once. Initialize eagerly if blocking is not an options.
			lock.lock();

			try {
				if (!initialized) {
					client.getPartitions();
					initialized = true;
				}
			} finally {
				lock.unlock();
			}
		}

		if (connectionType.equals(StatefulRedisPubSubConnection.class)
				|| connectionType.equals(StatefulRedisClusterPubSubConnection.class)) {

			return client.connectPubSubAsync(codec).thenApply(connectionType::cast);
		}

		if (StatefulRedisClusterConnection.class.isAssignableFrom(connectionType)
				|| connectionType.equals(StatefulConnection.class)) {

			return client.connectAsync(codec).thenApply(connection -> {
						getReadFrom().ifPresent(connection::setReadFrom);
						return connectionType.cast(connection);
					});
		}

		return LettuceFutureUtils
				.failed(new InvalidDataAccessApiUsageException("Connection type %s not supported".formatted(connectionType)));
	}

	@Override
	public RedisClusterClient getRedisClient() {
		return this.client;
	}
}
