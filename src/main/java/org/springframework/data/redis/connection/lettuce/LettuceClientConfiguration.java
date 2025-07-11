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

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SslVerifyMode;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.resource.ClientResources;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Redis client configuration for lettuce. This configuration provides optional configuration elements such as
 * {@link ClientResources} and {@link ClientOptions} specific to Lettuce client features.
 * <p>
 * Providing optional elements allows a more specific configuration of the client:
 * <ul>
 * <li>Whether to use SSL</li>
 * <li>Whether to verify peers using SSL</li>
 * <li>Whether to use StartTLS</li>
 * <li>Optional {@link ClientResources}</li>
 * <li>Optional {@link ClientOptions}, defaults to {@link ClientOptions} with enabled {@link TimeoutOptions}.</li>
 * <li>Optional client name</li>
 * <li>Optional {@link ReadFrom}. Enables Master/Replica operations if configured.</li>
 * <li>Client {@link Duration timeout}</li>
 * <li>Shutdown {@link Duration timeout}</li>
 * <li>Shutdown quiet {@link Duration period}</li>
 * </ul>
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Yanming Zhou
 * @author Zhian Chen
 * @since 2.0
 * @see org.springframework.data.redis.connection.RedisStandaloneConfiguration
 * @see org.springframework.data.redis.connection.RedisSentinelConfiguration
 * @see org.springframework.data.redis.connection.RedisClusterConfiguration
 */
public interface LettuceClientConfiguration {

	/**
	 * @return {@literal true} to use SSL, {@literal false} to use unencrypted connections.
	 */
	boolean isUseSsl();

	/**
	 * @return {@literal true} to verify peers when using {@link #isUseSsl() SSL}.
	 * @deprecated since 3.4, use {@link #getVerifyMode()} for how peer verification is configured.
	 */
	@Deprecated(since = "3.4")
	boolean isVerifyPeer();

	/**
	 * @return the {@link io.lettuce.core.SslVerifyMode}.
	 * @since 3.4
	 */
	SslVerifyMode getVerifyMode();

	/**
	 * @return {@literal true} to use Start TLS ({@code true} if the first write request shouldn't be encrypted).
	 */
	boolean isStartTls();

	/**
	 * @return the optional {@link ClientResources}.
	 */
	Optional<ClientResources> getClientResources();

	/**
	 * @return the optional {@link io.lettuce.core.ClientOptions}.
	 */
	Optional<ClientOptions> getClientOptions();

	/**
	 * @return the optional client name to be set with {@code CLIENT SETNAME}.
	 * @since 2.1
	 */
	Optional<String> getClientName();

	/**
	 * @return the optional {@link io.lettuce.core.ReadFrom} setting.
	 * @since 2.1
	 */
	Optional<ReadFrom> getReadFrom();

	/**
	 * @return the optional {@link RedisCredentialsProviderFactory}.
	 * @since 3.0
	 */
	Optional<RedisCredentialsProviderFactory> getRedisCredentialsProviderFactory();

	/**
	 * @return the timeout.
	 */
	Duration getCommandTimeout();

	/**
	 * @return the shutdown timeout used to close the client.
	 * @see io.lettuce.core.AbstractRedisClient#shutdown(long, long, TimeUnit)
	 */
	Duration getShutdownTimeout();

	/**
	 * @return the shutdown quiet period used to close the client.
	 * @since 2.2
	 * @see io.lettuce.core.AbstractRedisClient#shutdown(long, long, TimeUnit)
	 */
	Duration getShutdownQuietPeriod();

	/**
	 * Creates a new {@link LettuceClientConfigurationBuilder} to build {@link LettuceClientConfiguration} to be used with
	 * the Lettuce client.
	 *
	 * @return a new {@link LettuceClientConfigurationBuilder} to build {@link LettuceClientConfiguration}.
	 */
	static LettuceClientConfigurationBuilder builder() {
		return new LettuceClientConfigurationBuilder();
	}

	/**
	 * Creates a default {@link LettuceClientConfiguration} with:
	 * <dl>
	 * <dt>SSL</dt>
	 * <dd>no</dd>
	 * <dt>Peer Verification</dt>
	 * <dd>yes</dd>
	 * <dt>Start TLS</dt>
	 * <dd>no</dd>
	 * <dt>Client Options</dt>
	 * <dd>{@link ClientOptions} with enabled {@link io.lettuce.core.TimeoutOptions}</dd>
	 * <dt>Client Resources</dt>
	 * <dd>none</dd>
	 * <dt>Client name</dt>
	 * <dd>none</dd>
	 * <dt>Read From</dt>
	 * <dd>none</dd>
	 * <dt>Connect Timeout</dt>
	 * <dd>60 Seconds</dd>
	 * <dt>Shutdown Timeout</dt>
	 * <dd>100 Milliseconds</dd>
	 * <dt>Shutdown Quiet Period</dt>
	 * <dd>100 Milliseconds</dd>
	 * </dl>
	 *
	 * @return a {@link LettuceClientConfiguration} with defaults.
	 */
	static LettuceClientConfiguration defaultConfiguration() {
		return builder().build();
	}

	/**
	 * @author Mark Paluch
	 * @author Christoph Strobl
	 */
	class LettuceClientConfigurationBuilder {

		boolean useSsl;
		SslVerifyMode verifyMode = SslVerifyMode.FULL;
		boolean startTls;
		@Nullable ClientResources clientResources;
		ClientOptions clientOptions = ClientOptions.builder().timeoutOptions(TimeoutOptions.enabled()).build();
		@Nullable String clientName;
		@Nullable ReadFrom readFrom;
		@Nullable RedisCredentialsProviderFactory redisCredentialsProviderFactory;
		Duration timeout = Duration.ofSeconds(RedisURI.DEFAULT_TIMEOUT);
		Duration shutdownTimeout = Duration.ofMillis(100);
		Duration shutdownQuietPeriod = Duration.ZERO;

		LettuceClientConfigurationBuilder() {}

		/**
		 * Apply SSL settings, command timeout, and client name from a {@link RedisURI}.
		 *
		 * @param redisUri the connection URI.
		 * @return {@literal this} builder.
		 * @since 2.5.3
		 */
		public LettuceClientConfigurationBuilder apply(RedisURI redisUri) {

			this.useSsl = redisUri.isSsl();
			this.verifyMode = redisUri.getVerifyMode();
			this.startTls = redisUri.isStartTls();

			if (!redisUri.getTimeout().equals(RedisURI.DEFAULT_TIMEOUT_DURATION)) {
				this.timeout = redisUri.getTimeout();
			}

			if (!ObjectUtils.isEmpty(redisUri.getClientName())) {
				this.clientName = redisUri.getClientName();
			}

			return this;
		}

		/**
		 * Enable SSL connections.
		 *
		 * @return {@link LettuceSslClientConfigurationBuilder}.
		 */
		public LettuceSslClientConfigurationBuilder useSsl() {

			this.useSsl = true;
			return new LettuceSslClientConfigurationBuilder(this);
		}

		/**
		 * Configure {@link ClientResources}.
		 *
		 * @param clientResources must not be {@literal null}.
		 * @return {@literal this} builder.
		 * @throws IllegalArgumentException if clientResources is {@literal null}.
		 */
		public LettuceClientConfigurationBuilder clientResources(ClientResources clientResources) {

			Assert.notNull(clientResources, "ClientResources must not be null");

			this.clientResources = clientResources;
			return this;
		}

		/**
		 * Configure {@link ClientOptions}.
		 *
		 * @param clientOptions must not be {@literal null}.
		 * @return {@literal this} builder.
		 * @throws IllegalArgumentException if clientOptions is {@literal null}.
		 */
		public LettuceClientConfigurationBuilder clientOptions(ClientOptions clientOptions) {

			Assert.notNull(clientOptions, "ClientOptions must not be null");

			this.clientOptions = clientOptions;
			return this;
		}

		/**
		 * Configure {@link ReadFrom}. Enables Master/Replica operations if configured.
		 *
		 * @param readFrom must not be {@literal null}.
		 * @return {@literal this} builder.
		 * @throws IllegalArgumentException if readFrom is {@literal null}.
		 * @since 2.1
		 */
		public LettuceClientConfigurationBuilder readFrom(ReadFrom readFrom) {

			Assert.notNull(readFrom, "ReadFrom must not be null");

			this.readFrom = readFrom;
			return this;
		}

		/**
		 * Configure a {@link RedisCredentialsProviderFactory} to obtain {@link io.lettuce.core.RedisCredentialsProvider}
		 * instances to support credential rotation.
		 *
		 * @param redisCredentialsProviderFactory must not be {@literal null}.
		 * @return {@literal this} builder.
		 * @throws IllegalArgumentException if redisCredentialsProviderFactory is {@literal null}.
		 * @since 3.0
		 */
		public LettuceClientConfigurationBuilder redisCredentialsProviderFactory(
				RedisCredentialsProviderFactory redisCredentialsProviderFactory) {

			Assert.notNull(redisCredentialsProviderFactory, "RedisCredentialsProviderFactory must not be null");

			this.redisCredentialsProviderFactory = redisCredentialsProviderFactory;
			return this;
		}

		/**
		 * Configure a {@code clientName} to be set with {@code CLIENT SETNAME}.
		 *
		 * @param clientName must not be {@literal null} or empty.
		 * @return {@literal this} builder.
		 * @throws IllegalArgumentException if clientName is {@literal null} or empty.
		 * @since 2.1
		 */
		public LettuceClientConfigurationBuilder clientName(String clientName) {

			Assert.hasText(clientName, "Client name must not be null or empty");

			this.clientName = clientName;
			return this;
		}

		/**
		 * Configure a command timeout.
		 *
		 * @param timeout must not be {@literal null}.
		 * @return {@literal this} builder.
		 * @throws IllegalArgumentException if timeout is {@literal null}.
		 */
		public LettuceClientConfigurationBuilder commandTimeout(Duration timeout) {

			Assert.notNull(timeout, "Duration must not be null");

			this.timeout = timeout;
			return this;
		}

		/**
		 * Configure a shutdown timeout.
		 *
		 * @param shutdownTimeout must not be {@literal null}.
		 * @return {@literal this} builder.
		 * @throws IllegalArgumentException if shutdownTimeout is {@literal null}.
		 */
		public LettuceClientConfigurationBuilder shutdownTimeout(Duration shutdownTimeout) {

			Assert.notNull(shutdownTimeout, "Duration must not be null");

			this.shutdownTimeout = shutdownTimeout;
			return this;
		}

		/**
		 * Configure the shutdown quiet period.
		 *
		 * @param shutdownQuietPeriod must not be {@literal null}.
		 * @return {@literal this} builder.
		 * @throws IllegalArgumentException if shutdownQuietPeriod is {@literal null}.
		 * @since 2.2
		 */
		public LettuceClientConfigurationBuilder shutdownQuietPeriod(Duration shutdownQuietPeriod) {

			Assert.notNull(shutdownQuietPeriod, "Duration must not be null");

			this.shutdownQuietPeriod = shutdownQuietPeriod;
			return this;
		}

		/**
		 * Build the {@link LettuceClientConfiguration} with the configuration applied from this builder.
		 *
		 * @return a new {@link LettuceClientConfiguration} object.
		 */
		public LettuceClientConfiguration build() {

			return new DefaultLettuceClientConfiguration(useSsl, verifyMode, startTls, clientResources, clientOptions,
					clientName, readFrom, redisCredentialsProviderFactory, timeout, shutdownTimeout, shutdownQuietPeriod);
		}
	}

	/**
	 * Builder for SSL-related {@link LettuceClientConfiguration}.
	 */
	class LettuceSslClientConfigurationBuilder {

		private final LettuceClientConfigurationBuilder delegate;

		LettuceSslClientConfigurationBuilder(LettuceClientConfigurationBuilder delegate) {

			Assert.notNull(delegate, "Delegate client configuration builder must not be null");
			this.delegate = delegate;
		}

		/**
		 * Configure peer verification.
		 *
		 * @return {@literal this} builder.
		 * @since 3.4
		 */
		public LettuceSslClientConfigurationBuilder verifyPeer(SslVerifyMode verifyMode) {

			Assert.notNull(verifyMode, "SslVerifyMode must not be null");

			delegate.verifyMode = verifyMode;
			return this;
		}

		/**
		 * Disable peer verification.
		 *
		 * @return {@literal this} builder.
		 */
		public LettuceSslClientConfigurationBuilder disablePeerVerification() {
			return verifyPeer(SslVerifyMode.NONE);
		}

		/**
		 * Enable Start TLS to send the first bytes unencrypted.
		 *
		 * @return {@literal this} builder.
		 */
		public LettuceSslClientConfigurationBuilder startTls() {

			delegate.startTls = true;
			return this;
		}

		/**
		 * Return to {@link LettuceClientConfigurationBuilder}.
		 *
		 * @return {@link LettuceClientConfigurationBuilder}.
		 */
		public LettuceClientConfigurationBuilder and() {
			return delegate;
		}

		/**
		 * Build the {@link LettuceClientConfiguration} with the configuration applied from this builder.
		 *
		 * @return a new {@link LettuceClientConfiguration} object.
		 */
		public LettuceClientConfiguration build() {
			return delegate.build();
		}
	}
}
