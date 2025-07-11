/*
 * Copyright 2018-2025 the original author or authors.
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
package org.springframework.data.redis.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.reactivestreams.Publisher;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.ByteBufferRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoConsumer;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoStream;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.hash.HashMapper;
import org.springframework.util.Assert;

/**
 * Reactive Redis operations for Stream Commands.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Dengliming
 * @author Marcin Zielinski
 * @author John Blum
 * @author jinkshower
 * @since 2.2
 */
@NullUnmarked
public interface ReactiveStreamOperations<K, HK, HV> extends HashMapperProvider<HK, HV> {

	/**
	 * Acknowledge one or more records as processed.
	 *
	 * @param key the stream key.
	 * @param group name of the consumer group.
	 * @param recordIds record Id's to acknowledge.
	 * @return the {@link Mono} emitting the length of acknowledged records.
	 * @see <a href="https://redis.io/commands/xack">Redis Documentation: XACK</a>
	 */
	default Mono<Long> acknowledge(@NonNull K key, @NonNull String group, @NonNull String @NonNull... recordIds) {
		return acknowledge(key, group, Arrays.stream(recordIds).map(RecordId::of).toArray(RecordId[]::new));
	}

	/**
	 * Acknowledge one or more records as processed.
	 *
	 * @param key the stream key.
	 * @param group name of the consumer group.
	 * @param recordIds record Id's to acknowledge.
	 * @return the {@link Mono} emitting the length of acknowledged records.
	 * @see <a href="https://redis.io/commands/xack">Redis Documentation: XACK</a>
	 */
	Mono<Long> acknowledge(@NonNull K key, @NonNull String group, @NonNull RecordId @NonNull... recordIds);

	/**
	 * Acknowledge the given record as processed.
	 *
	 * @param group name of the consumer group.
	 * @param record the {@link Record} to acknowledge.
	 * @return the {@link Mono} emitting the length of acknowledged records.
	 * @see <a href="https://redis.io/commands/xack">Redis Documentation: XACK</a>
	 */
	default Mono<Long> acknowledge(@NonNull String group, @NonNull Record<K, ?> record) {
		return acknowledge(record.getRequiredStream(), group, record.getId());
	}

	/**
	 * Append a record to the stream {@code key} with the specified options.
	 *
	 * @param key the stream key.
	 * @param content record content as Map.
	 * @param xAddOptions parameters for the {@literal XADD} call.
	 * @return the {@link Mono} emitting the {@link RecordId}.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 * @since 3.4
	 */
	default Mono<RecordId> add(@NonNull K key, @NonNull Map<? extends HK, ? extends HV> content,
			@NonNull XAddOptions xAddOptions) {
		return add(StreamRecords.newRecord().in(key).ofMap(content), xAddOptions);
	}

	/**
	 * Append a record, backed by a {@link Map} holding the field/value pairs, to the stream with the specified options.
	 *
	 * @param record the record to append.
	 * @param xAddOptions parameters for the {@literal XADD} call.
	 * @return the {@link Mono} emitting the {@link RecordId}.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 * @since 3.4
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	default Mono<RecordId> add(@NonNull MapRecord<K, ? extends HK, ? extends HV> record,
			@NonNull XAddOptions xAddOptions) {
		return add((Record) record, xAddOptions);
	}

	/**
	 * Append the record, backed by the given value, to the stream with the specified options. The value will be hashed
	 * and serialized.
	 *
	 * @param record must not be {@literal null}.
	 * @param xAddOptions parameters for the {@literal XADD} call. Must not be {@literal null}.
	 * @return the {@link Mono} emitting the {@link RecordId}.
	 * @see MapRecord
	 * @see ObjectRecord
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 * @since 3.4
	 */
	Mono<RecordId> add(@NonNull Record<K, ?> record, @NonNull XAddOptions xAddOptions);

	/**
	 * Append one or more records to the stream {@code key}.
	 *
	 * @param key the stream key.
	 * @param bodyPublisher record body {@link Publisher}.
	 * @return the record Ids.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 */
	default Flux<RecordId> add(@NonNull K key,
			@NonNull Publisher<? extends Map<? extends HK, ? extends HV>> bodyPublisher) {
		return Flux.from(bodyPublisher).flatMap(it -> add(key, it));
	}

	/**
	 * Append a record to the stream {@code key}.
	 *
	 * @param key the stream key.
	 * @param content record content as Map.
	 * @return the {@link Mono} emitting the {@link RecordId}.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 */
	default Mono<RecordId> add(@NonNull K key, @NonNull Map<? extends HK, ? extends HV> content) {
		return add(StreamRecords.newRecord().in(key).ofMap(content));
	}

	/**
	 * Append a record, backed by a {@link Map} holding the field/value pairs, to the stream.
	 *
	 * @param record the record to append.
	 * @return the {@link Mono} emitting the {@link RecordId}.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 */
	@SuppressWarnings("unchecked")
	default Mono<RecordId> add(@NonNull MapRecord<K, ? extends HK, ? extends HV> record) {
		return add((Record) record);
	}

	/**
	 * Append the record, backed by the given value, to the stream. The value will be hashed and serialized.
	 *
	 * @param record must not be {@literal null}.
	 * @return the {@link Mono} emitting the {@link RecordId}.
	 * @see MapRecord
	 * @see ObjectRecord
	 */
	Mono<RecordId> add(@NonNull Record<K, ?> record);

	/**
	 * Changes the ownership of a pending message so that the new owner is the consumer specified as the command argument.
	 * The message is claimed only if its idle time (ms) is greater than the {@link Duration minimum idle time} specified
	 * when calling {@literal XCLAIM}.
	 *
	 * @param key {@link K key} to the steam.
	 * @param consumerGroup {@link String name} of the consumer group.
	 * @param newOwner {@link String name} of the consumer claiming the message.
	 * @param minIdleTime {@link Duration minimum idle time} required for a message to be claimed.
	 * @param recordIds {@link RecordId record IDs} to be claimed.
	 * @return {@link Flux} of claimed {@link MapRecord MapRecords}.
	 * @see <a href="https://redis.io/commands/xclaim/">Redis Documentation: XCLAIM</a>
	 * @see org.springframework.data.redis.connection.stream.MapRecord
	 * @see org.springframework.data.redis.connection.stream.RecordId
	 * @see #claim(Object, String, String, XClaimOptions)
	 * @see reactor.core.publisher.Flux
	 */
	default Flux<MapRecord<K, HK, HV>> claim(@NonNull K key, @NonNull String consumerGroup, @NonNull String newOwner,
			@NonNull Duration minIdleTime, @NonNull RecordId @NonNull... recordIds) {

		return claim(key, consumerGroup, newOwner, XClaimOptions.minIdle(minIdleTime).ids(recordIds));
	}

	/**
	 * Changes the ownership of a pending message so that the new owner is the consumer specified as the command argument.
	 * The message is claimed only if its idle time (ms) is greater than the given {@link Duration minimum idle time}
	 * specified when calling {@literal XCLAIM}.
	 *
	 * @param key {@link K key} to the steam.
	 * @param consumerGroup {@link String name} of the consumer group.
	 * @param newOwner {@link String name} of the consumer claiming the message.
	 * @param xClaimOptions additional parameters for the {@literal CLAIM} call.
	 * @return a {@link Flux} of claimed {@link MapRecord MapRecords}.
	 * @see <a href="https://redis.io/commands/xclaim/">Redis Documentation: XCLAIM</a>
	 * @see org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions
	 * @see org.springframework.data.redis.connection.stream.MapRecord
	 * @see reactor.core.publisher.Flux
	 */
	Flux<MapRecord<K, HK, HV>> claim(@NonNull K key, @NonNull String consumerGroup, @NonNull String newOwner,
			@NonNull XClaimOptions xClaimOptions);

	/**
	 * Removes the specified records from the stream. Returns the number of records deleted, that may be different from
	 * the number of IDs passed in case certain IDs do not exist.
	 *
	 * @param key the stream key.
	 * @param recordIds stream record Id's.
	 * @return the {@link Mono} emitting the number of removed records.
	 * @see <a href="https://redis.io/commands/xdel">Redis Documentation: XDEL</a>
	 */
	default Mono<Long> delete(@NonNull K key, @NonNull String @NonNull... recordIds) {
		return delete(key, Arrays.stream(recordIds).map(RecordId::of).toArray(RecordId[]::new));
	}

	/**
	 * Removes a given {@link Record} from the stream.
	 *
	 * @param record must not be {@literal null}.
	 * @return he {@link Mono} emitting the number of removed records.
	 */
	default Mono<Long> delete(@NonNull Record<K, ?> record) {

		Assert.notNull(record.getStream(), "Record.getStream() must not be null");
		return delete(record.getStream(), record.getId());
	}

	/**
	 * Removes the specified records from the stream. Returns the number of records deleted, that may be different from
	 * the number of IDs passed in case certain IDs do not exist.
	 *
	 * @param key the stream key.
	 * @param recordIds stream record Id's.
	 * @return the {@link Mono} emitting the number of removed records.
	 * @see <a href="https://redis.io/commands/xdel">Redis Documentation: XDEL</a>
	 */
	Mono<Long> delete(@NonNull K key, @NonNull RecordId @NonNull... recordIds);

	/**
	 * Create a consumer group at the {@link ReadOffset#latest() latest offset}. This command creates the stream if it
	 * does not already exist.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param group name of the consumer group.
	 * @return the {@link Mono} emitting {@literal OK} if successful.. {@literal null} when used in pipeline /
	 *         transaction.
	 */
	default Mono<String> createGroup(@NonNull K key, @NonNull String group) {
		return createGroup(key, ReadOffset.latest(), group);
	}

	/**
	 * Create a consumer group. This command creates the stream if it does not already exist.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param readOffset the {@link ReadOffset} to apply.
	 * @param group name of the consumer group.
	 * @return the {@link Mono} emitting {@literal OK} if successful.
	 */
	Mono<String> createGroup(@NonNull K key, @NonNull ReadOffset readOffset, @NonNull String group);

	/**
	 * Delete a consumer from a consumer group.
	 *
	 * @param key the stream key.
	 * @param consumer consumer identified by group name and consumer key.
	 * @return the {@link Mono} {@literal OK} if successful. {@literal null} when used in pipeline / transaction.
	 */
	Mono<String> deleteConsumer(@NonNull K key, @NonNull Consumer consumer);

	/**
	 * Destroy a consumer group.
	 *
	 * @param key the stream key.
	 * @param group name of the consumer group.
	 * @return the {@link Mono} {@literal OK} if successful. {@literal null} when used in pipeline / transaction.
	 */
	Mono<String> destroyGroup(@NonNull K key, @NonNull String group);

	/**
	 * Obtain information about every consumer in a specific {@literal consumer group} for the stream stored at the
	 * specified {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param group name of the {@literal consumer group}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	Flux<XInfoConsumer> consumers(@NonNull K key, @NonNull String group);

	/**
	 * Obtain information about {@literal consumer groups} associated with the stream stored at the specified
	 * {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	Flux<XInfoGroup> groups(@NonNull K key);

	/**
	 * Obtain general information about the stream stored at the specified {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	Mono<XInfoStream> info(@NonNull K key);

	/**
	 * Obtain the {@link PendingMessagesSummary} for a given {@literal consumer group}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param group the name of the {@literal consumer group}. Must not be {@literal null}.
	 * @return a summary of pending messages within the given {@literal consumer group} or {@literal null} when used in
	 *         pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xpending">Redis Documentation: xpending</a>
	 * @since 2.3
	 */
	Mono<PendingMessagesSummary> pending(@NonNull K key, @NonNull String group);

	/**
	 * Obtained detailed information about all pending messages for a given {@link Consumer}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param consumer the consumer to fetch {@link PendingMessages} for. Must not be {@literal null}.
	 * @return pending messages for the given {@link Consumer} or {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xpending">Redis Documentation: xpending</a>
	 * @since 2.3
	 */
	default Mono<PendingMessages> pending(@NonNull K key, @NonNull Consumer consumer) {
		return pending(key, consumer, Range.unbounded(), -1L);
	}

	/**
	 * Obtain detailed information about pending {@link PendingMessage messages} for a given {@link Range} within a
	 * {@literal consumer group}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param group the name of the {@literal consumer group}. Must not be {@literal null}.
	 * @param range the range of messages ids to search within. Must not be {@literal null}.
	 * @param count limit the number of results.
	 * @return pending messages for the given {@literal consumer group} or {@literal null} when used in pipeline /
	 *         transaction.
	 * @see <a href="https://redis.io/commands/xpending">Redis Documentation: xpending</a>
	 * @since 2.3
	 */
	Mono<PendingMessages> pending(@NonNull K key, @NonNull String group, @NonNull Range<?> range, long count);

	/**
	 * Obtain detailed information about pending {@link PendingMessage messages} for a given {@link Range} and
	 * {@link Consumer} within a {@literal consumer group}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param consumer the name of the {@link Consumer}. Must not be {@literal null}.
	 * @param range the range of messages ids to search within. Must not be {@literal null}.
	 * @param count limit the number of results.
	 * @return pending messages for the given {@link Consumer} or {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xpending">Redis Documentation: xpending</a>
	 * @since 2.3
	 */
	Mono<PendingMessages> pending(@NonNull K key, @NonNull Consumer consumer, @NonNull Range<?> range, long count);

	/**
	 * Get the length of a stream.
	 *
	 * @param key the stream key.
	 * @return the {@link Mono} emitting the length of the stream.
	 * @see <a href="https://redis.io/commands/xlen">Redis Documentation: XLEN</a>
	 */
	Mono<Long> size(@NonNull K key);

	/**
	 * Read records from a stream within a specific {@link Range}.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xrange">Redis Documentation: XRANGE</a>
	 */
	default Flux<MapRecord<K, HK, HV>> range(@NonNull K key, @NonNull Range<String> range) {
		return range(key, range, Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a {@link Limit}.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return lthe {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xrange">Redis Documentation: XRANGE</a>
	 */
	Flux<MapRecord<K, HK, HV>> range(@NonNull K key, @NonNull Range<String> range, @NonNull Limit limit);

	/**
	 * Read all records from a stream within a specific {@link Range}.
	 *
	 * @param targetType the target type of the payload.
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xrange">Redis Documentation: XRANGE</a>
	 */
	default <V> Flux<ObjectRecord<K, V>> range(@NonNull Class<V> targetType, @NonNull K key,
			@NonNull Range<String> range) {
		return range(targetType, key, range, Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a {@link Limit}.
	 *
	 * @param targetType the target type of the payload.
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xrange">Redis Documentation: XRANGE</a>
	 */
	default <V> Flux<ObjectRecord<K, V>> range(@NonNull Class<V> targetType, @NonNull K key, @NonNull Range<String> range,
			@NonNull Limit limit) {

		Assert.notNull(targetType, "Target type must not be null");

		return range(key, range, limit).map(it -> map(it, targetType));
	}

	/**
	 * Read records from a {@link StreamOffset} as {@link ObjectRecord}.
	 *
	 * @param stream the stream to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	@SuppressWarnings("unchecked")
	default Flux<MapRecord<K, HK, HV>> read(@NonNull StreamOffset<K> stream) {

		Assert.notNull(stream, "StreamOffset must not be null");

		return read(StreamReadOptions.empty(), new StreamOffset[] { stream });
	}

	/**
	 * Read records from a {@link StreamOffset} as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param stream the stream to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	@SuppressWarnings("unchecked")
	default <V> Flux<ObjectRecord<K, V>> read(@NonNull Class<V> targetType, @NonNull StreamOffset<K> stream) {

		Assert.notNull(stream, "StreamOffset must not be null");

		return read(targetType, StreamReadOptions.empty(), new StreamOffset[] { stream });
	}

	/**
	 * Read records from one or more {@link StreamOffset}s.
	 *
	 * @param streams the streams to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	default Flux<MapRecord<K, HK, HV>> read(@NonNull StreamOffset<K>... streams) {
		return read(StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param streams the streams to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	default <V> Flux<ObjectRecord<K, V>> read(@NonNull Class<V> targetType, @NonNull StreamOffset<K>... streams) {
		return read(targetType, StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s.
	 *
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	Flux<MapRecord<K, HK, HV>> read(@NonNull StreamReadOptions readOptions, @NonNull StreamOffset<K> @NonNull... streams);

	/**
	 * Read records from one or more {@link StreamOffset}s as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	default <V> Flux<ObjectRecord<K, V>> read(@NonNull Class<V> targetType, @NonNull StreamReadOptions readOptions,
			@NonNull StreamOffset<K> @NonNull... streams) {

		Assert.notNull(targetType, "Target type must not be null");

		return read(readOptions, streams).map(it -> map(it, targetType));
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group.
	 *
	 * @param consumer consumer/group.
	 * @param streams the streams to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xreadgroup">Redis Documentation: XREADGROUP</a>
	 */
	default Flux<MapRecord<K, HK, HV>> read(@NonNull Consumer consumer, @NonNull StreamOffset<K> @NonNull... streams) {
		return read(consumer, StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param consumer consumer/group.
	 * @param streams the streams to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xreadgroup">Redis Documentation: XREADGROUP</a>
	 */
	default <V> Flux<ObjectRecord<K, V>> read(@NonNull Class<V> targetType, @NonNull Consumer consumer,
			@NonNull StreamOffset<K> @NonNull... streams) {
		return read(targetType, consumer, StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group.
	 *
	 * @param consumer consumer/group.
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xreadgroup">Redis Documentation: XREADGROUP</a>
	 */
	Flux<MapRecord<K, HK, HV>> read(@NonNull Consumer consumer, @NonNull StreamReadOptions readOptions,
			@NonNull StreamOffset<K>... streams);

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param consumer consumer/group.
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xreadgroup">Redis Documentation: XREADGROUP</a>
	 */
	default <V> Flux<ObjectRecord<K, V>> read(@NonNull Class<V> targetType, @NonNull Consumer consumer,
			@NonNull StreamReadOptions readOptions, @NonNull StreamOffset<K> @NonNull... streams) {

		Assert.notNull(targetType, "Target type must not be null");

		return read(consumer, readOptions, streams).map(it -> map(it, targetType));
	}

	/**
	 * Read records from a stream within a specific {@link Range} in reverse order.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xrevrange">Redis Documentation: XREVRANGE</a>
	 */
	default Flux<MapRecord<K, HK, HV>> reverseRange(@NonNull K key, @NonNull Range<String> range) {
		return reverseRange(key, range, Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a {@link Limit} in reverse order.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xrevrange">Redis Documentation: XREVRANGE</a>
	 */
	Flux<MapRecord<K, HK, HV>> reverseRange(@NonNull K key, @NonNull Range<String> range, @NonNull Limit limit);

	/**
	 * Read records from a stream within a specific {@link Range} in reverse order as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xrevrange">Redis Documentation: XREVRANGE</a>
	 */
	default <V> Flux<ObjectRecord<K, V>> reverseRange(@NonNull Class<V> targetType, @NonNull K key,
			@NonNull Range<String> range) {
		return reverseRange(targetType, key, range, Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a {@link Limit} in reverse order as
	 * {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return the {@link Flux} emitting records one by one.
	 * @see <a href="https://redis.io/commands/xrevrange">Redis Documentation: XREVRANGE</a>
	 */
	default <V> Flux<ObjectRecord<K, V>> reverseRange(@NonNull Class<V> targetType, @NonNull K key,
			@NonNull Range<String> range, @NonNull Limit limit) {

		Assert.notNull(targetType, "Target type must not be null");

		return reverseRange(key, range, limit).map(it -> map(it, targetType));
	}

	/**
	 * Trims the stream to {@code count} elements.
	 *
	 * @param key the stream key.
	 * @param count length of the stream.
	 * @return number of removed entries.
	 * @see <a href="https://redis.io/commands/xtrim">Redis Documentation: XTRIM</a>
	 */
	Mono<Long> trim(@NonNull K key, long count);

	/**
	 * Trims the stream to {@code count} elements.
	 *
	 * @param key the stream key.
	 * @param count length of the stream.
	 * @param approximateTrimming the trimming must be performed in a approximated way in order to maximize performances.
	 * @return number of removed entries.
	 * @since 2.4
	 * @see <a href="https://redis.io/commands/xtrim">Redis Documentation: XTRIM</a>
	 */
	Mono<Long> trim(@NonNull K key, long count, boolean approximateTrimming);

	/**
	 * Get the {@link HashMapper} for a specific type.
	 *
	 * @param targetType must not be {@literal null}.
	 * @param <V>
	 * @return the {@link HashMapper} suitable for a given type;
	 */
	@Override
	<V> HashMapper<V, HK, HV> getHashMapper(@NonNull Class<V> targetType);

	/**
	 * Map records from {@link MapRecord} to {@link ObjectRecord}.
	 *
	 * @param record the stream records to map.
	 * @param targetType the target type of the payload.
	 * @return the mapped {@link ObjectRecord}.
	 * @since 2.x
	 */
	default <V> ObjectRecord<K, V> map(@NonNull MapRecord<K, HK, HV> record, @NonNull Class<V> targetType) {

		Assert.notNull(record, "Records must not be null");
		Assert.notNull(targetType, "Target type must not be null");

		return StreamObjectMapper.toObjectRecord(record, this, targetType);
	}

	/**
	 * Deserialize a {@link ByteBufferRecord} using the configured serialization context into a {@link MapRecord}.
	 *
	 * @param record the stream record to map.
	 * @return deserialized {@link MapRecord}.
	 * @since 2.x
	 */
	MapRecord<K, HK, HV> deserializeRecord(@NonNull ByteBufferRecord record);
}
