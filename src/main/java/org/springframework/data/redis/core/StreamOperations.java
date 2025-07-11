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

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoConsumers;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoStream;
import org.springframework.data.redis.hash.HashMapper;
import org.springframework.util.Assert;

/**
 * Redis stream specific operations.
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
public interface StreamOperations<K, HK, HV> extends HashMapperProvider<HK, HV> {

	/**
	 * Acknowledge one or more records as processed.
	 *
	 * @param key the stream key.
	 * @param group name of the consumer group.
	 * @param recordIds record id's to acknowledge.
	 * @return length of acknowledged records. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xack">Redis Documentation: XACK</a>
	 */
	Long acknowledge(@NonNull K key, @NonNull String group, @NonNull String @NonNull... recordIds);

	/**
	 * Acknowledge one or more records as processed.
	 *
	 * @param key the stream key.
	 * @param group name of the consumer group.
	 * @param recordIds record id's to acknowledge.
	 * @return length of acknowledged records. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xack">Redis Documentation: XACK</a>
	 */
	default Long acknowledge(@NonNull K key, @NonNull String group, @NonNull RecordId @NonNull... recordIds) {
		return acknowledge(key, group, Arrays.stream(recordIds).map(RecordId::getValue).toArray(String[]::new));
	}

	/**
	 * Acknowledge the given record as processed.
	 *
	 * @param group name of the consumer group.
	 * @param record the {@link Record} to acknowledge.
	 * @return length of acknowledged records. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xack">Redis Documentation: XACK</a>
	 */
	default Long acknowledge(@NonNull String group, @NonNull Record<@NonNull K, ?> record) {
		return acknowledge(record.getRequiredStream(), group, record.getId());
	}

	/**
	 * Append a record to the stream {@code key} with the specified options.
	 *
	 * @param key the stream key.
	 * @param content record content as Map.
	 * @param xAddOptions additional parameters for the {@literal XADD} call.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 * @since 3.4
	 */
	default RecordId add(@NonNull K key, @NonNull Map<? extends @NonNull HK, ? extends HV> content,
			@NonNull XAddOptions xAddOptions) {
		return add(StreamRecords.newRecord().in(key).ofMap(content), xAddOptions);
	}

	/**
	 * Append a record, backed by a {@link Map} holding the field/value pairs, to the stream with the specified options.
	 *
	 * @param record the record to append.
	 * @param xAddOptions additional parameters for the {@literal XADD} call.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 * @since 3.4
	 */
	@SuppressWarnings("unchecked")
	default RecordId add(@NonNull MapRecord<K, ? extends HK, ? extends HV> record, @NonNull XAddOptions xAddOptions) {
		return add((Record) record, xAddOptions);
	}

	/**
	 * Append the record, backed by the given value, to the stream with the specified options. The value will be hashed
	 * and serialized.
	 *
	 * @param record must not be {@literal null}.
	 * @param xAddOptions parameters for the {@literal XADD} call. Must not be {@literal null}.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @see MapRecord
	 * @see ObjectRecord
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 * @since 3.4
	 */
	@SuppressWarnings("unchecked")
	RecordId add(@NonNull Record<K, ?> record, @NonNull XAddOptions xAddOptions);

	/**
	 * Append a record to the stream {@code key}.
	 *
	 * @param key the stream key.
	 * @param content record content as Map.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 */
	@SuppressWarnings("unchecked")
	default RecordId add(@NonNull K key, @NonNull Map<? extends @NonNull HK, ? extends HV> content) {
		return add(StreamRecords.newRecord().in(key).ofMap(content));
	}

	/**
	 * Append a record, backed by a {@link Map} holding the field/value pairs, to the stream.
	 *
	 * @param record the record to append.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xadd">Redis Documentation: XADD</a>
	 */
	@SuppressWarnings("unchecked")
	default RecordId add(@NonNull MapRecord<K, ? extends HK, ? extends HV> record) {
		return add((Record) record);
	}

	/**
	 * Append the record, backed by the given value, to the stream. The value is mapped as hash and serialized.
	 *
	 * @param record must not be {@literal null}.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @see MapRecord
	 * @see ObjectRecord
	 */
	@SuppressWarnings("unchecked")
	RecordId add(@NonNull Record<K, ?> record);

	/**
	 * Changes the ownership of a pending message so that the new owner is the consumer specified as the command argument.
	 * The message is claimed only if its idle time (ms) is greater than the given {@link Duration minimum idle time}
	 * specified when calling {@literal XCLAIM}.
	 *
	 * @param key {@link @NonNull K key} to the steam.
	 * @param consumerGroup {@link String name} of the consumer group.
	 * @param newOwner {@link String name} of the consumer claiming the message.
	 * @param minIdleTime {@link Duration minimum idle time} required for a message to be claimed.
	 * @param recordIds {@link RecordId record IDs} to be claimed.
	 * @return {@link List} of claimed {@link MapRecord MapRecords}.
	 * @see <a href="https://redis.io/commands/xclaim/">Redis Documentation: XCLAIM</a>
	 * @see org.springframework.data.redis.connection.stream.MapRecord
	 * @see org.springframework.data.redis.connection.stream.RecordId
	 * @see #claim(Object, String, String, XClaimOptions)
	 */
	default List<@NonNull MapRecord<K, HK, HV>> claim(@NonNull K key, @NonNull String consumerGroup,
			@NonNull String newOwner, @NonNull Duration minIdleTime, @NonNull RecordId @NonNull... recordIds) {

		return claim(key, consumerGroup, newOwner, XClaimOptions.minIdle(minIdleTime).ids(recordIds));
	}

	/**
	 * Changes the ownership of a pending message so that the new owner is the consumer specified as the command argument.
	 * The message is claimed only if its idle time (ms) is greater than the given {@link Duration minimum idle time}
	 * specified when calling {@literal XCLAIM}.
	 *
	 * @param key {@link @NonNull K key} to the steam.
	 * @param consumerGroup {@link String name} of the consumer group.
	 * @param newOwner {@link String name} of the consumer claiming the message.
	 * @param xClaimOptions additional parameters for the {@literal CLAIM} call.
	 * @return {@link List} of claimed {@link MapRecord MapRecords}.
	 * @see <a href="https://redis.io/commands/xclaim/">Redis Documentation: XCLAIM</a>
	 * @see org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions
	 * @see org.springframework.data.redis.connection.stream.MapRecord
	 */
	List<@NonNull MapRecord<K, HK, HV>> claim(@NonNull K key, @NonNull String consumerGroup, @NonNull String newOwner,
			@NonNull XClaimOptions xClaimOptions);

	/**
	 * Removes the specified records from the stream. Returns the number of records deleted, that may be different from
	 * the number of IDs passed in case certain IDs do not exist.
	 *
	 * @param key the stream key.
	 * @param recordIds stream record Id's.
	 * @return number of removed entries. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xdel">Redis Documentation: XDEL</a>
	 */
	default Long delete(@NonNull K key, @NonNull String @NonNull... recordIds) {
		return delete(key, Arrays.stream(recordIds).map(RecordId::of).toArray(RecordId[]::new));
	}

	/**
	 * Removes a given {@link Record} from the stream.
	 *
	 * @param record must not be {@literal null}.
	 * @return he {@link Mono} emitting the number of removed records.
	 */
	default Long delete(@NonNull Record<K, ?> record) {
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
	Long delete(@NonNull K key, @NonNull RecordId @NonNull... recordIds);

	/**
	 * Create a consumer group at the {@link ReadOffset#latest() latest offset}. This command creates the stream if it
	 * does not already exist.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param group name of the consumer group.
	 * @return {@literal OK} if successful. {@literal null} when used in pipeline / transaction.
	 */
	default String createGroup(@NonNull K key, @NonNull String group) {
		return createGroup(key, ReadOffset.latest(), group);
	}

	/**
	 * Create a consumer group. This command creates the stream if it does not already exist.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param readOffset the {@link ReadOffset} to apply.
	 * @param group name of the consumer group.
	 * @return {@literal OK} if successful. {@literal null} when used in pipeline / transaction.
	 */
	String createGroup(@NonNull K key, @NonNull ReadOffset readOffset, @NonNull String group);

	/**
	 * Delete a consumer from a consumer group.
	 *
	 * @param key the stream key.
	 * @param consumer consumer identified by group name and consumer key.
	 * @return {@literal true} if successful. {@literal null} when used in pipeline / transaction.
	 */
	Boolean deleteConsumer(@NonNull K key, @NonNull Consumer consumer);

	/**
	 * Destroy a consumer group.
	 *
	 * @param key the stream key.
	 * @param group name of the consumer group.
	 * @return {@literal true} if successful. {@literal null} when used in pipeline / transaction.
	 */
	Boolean destroyGroup(@NonNull K key, @NonNull String group);

	/**
	 * Obtain information about every consumer in a specific {@literal consumer group} for the stream stored at the
	 * specified {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param group name of the {@literal consumer group}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	XInfoConsumers consumers(@NonNull K key, @NonNull String group);

	/**
	 * Obtain information about {@literal consumer groups} associated with the stream stored at the specified
	 * {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	XInfoGroups groups(@NonNull K key);

	/**
	 * Obtain general information about the stream stored at the specified {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	XInfoStream info(@NonNull K key);

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
	PendingMessagesSummary pending(@NonNull K key, @NonNull String group);

	/**
	 * Obtained detailed information about all pending messages for a given {@link Consumer}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param consumer the consumer to fetch {@link PendingMessages} for. Must not be {@literal null}.
	 * @return pending messages for the given {@link Consumer} or {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xpending">Redis Documentation: xpending</a>
	 * @since 2.3
	 */
	default PendingMessages pending(@NonNull K key, @NonNull Consumer consumer) {
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
	PendingMessages pending(@NonNull K key, @NonNull String group, @NonNull Range<?> range, long count);

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
	PendingMessages pending(@NonNull K key, @NonNull Consumer consumer, @NonNull Range<?> range, long count);

	/**
	 * Get the length of a stream.
	 *
	 * @param key the stream key.
	 * @return length of the stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xlen">Redis Documentation: XLEN</a>
	 */
	Long size(@NonNull K key);

	/**
	 * Read records from a stream within a specific {@link Range}.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xrange">Redis Documentation: XRANGE</a>
	 */
	default List<MapRecord<K, HK, HV>> range(@NonNull K key, @NonNull Range<String> range) {
		return range(key, range, Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a {@link Limit}.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xrange">Redis Documentation: XRANGE</a>
	 */
	List<MapRecord<K, HK, HV>> range(@NonNull K key, @NonNull Range<String> range, @NonNull Limit limit);

	/**
	 * Read all records from a stream within a specific {@link Range} as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xrange">Redis Documentation: XRANGE</a>
	 */
	default <V> List<@NonNull ObjectRecord<K, V>> range(@NonNull Class<V> targetType, @NonNull K key,
			@NonNull Range<String> range) {
		return range(targetType, key, range, Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a {@link Limit} as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xrange">Redis Documentation: XRANGE</a>
	 */
	default <V> List<@NonNull ObjectRecord<K, V>> range(@NonNull Class<V> targetType, @NonNull K key,
			@NonNull Range<String> range, @NonNull Limit limit) {

		Assert.notNull(targetType, "Target type must not be null");

		return map(range(key, range, limit), targetType);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s.
	 *
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	default List<@NonNull MapRecord<K, HK, HV>> read(StreamOffset<@NonNull K> @NonNull... streams) {
		return read(StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	default <V> List<@NonNull ObjectRecord<K, V>> read(@NonNull Class<V> targetType,
			StreamOffset<@NonNull K> @NonNull... streams) {
		return read(targetType, StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s.
	 *
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	List<@NonNull MapRecord<K, HK, HV>> read(@NonNull StreamReadOptions readOptions,
			StreamOffset<@NonNull K> @NonNull... streams);

	/**
	 * Read records from one or more {@link StreamOffset}s as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xread">Redis Documentation: XREAD</a>
	 */
	default <V> List<@NonNull ObjectRecord<K, V>> read(@NonNull Class<V> targetType,
			@NonNull StreamReadOptions readOptions, StreamOffset<@NonNull K> @NonNull... streams) {

		Assert.notNull(targetType, "Target type must not be null");

		return map(read(readOptions, streams), targetType);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group.
	 *
	 * @param consumer consumer/group.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xreadgroup">Redis Documentation: XREADGROUP</a>
	 */
	default List<@NonNull MapRecord<K, HK, HV>> read(@NonNull Consumer consumer,
			StreamOffset<@NonNull K> @NonNull... streams) {
		return read(consumer, StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param consumer consumer/group.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xreadgroup">Redis Documentation: XREADGROUP</a>
	 */
	default <V> List<ObjectRecord<K, V>> read(@NonNull Class<V> targetType, @NonNull Consumer consumer,
			StreamOffset<@NonNull K> @NonNull... streams) {
		return read(targetType, consumer, StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group.
	 *
	 * @param consumer consumer/group.
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xreadgroup">Redis Documentation: XREADGROUP</a>
	 */
	List<@NonNull MapRecord<K, HK, HV>> read(@NonNull Consumer consumer, @NonNull StreamReadOptions readOptions,
			StreamOffset<@NonNull K> @NonNull... streams);

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param consumer consumer/group.
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xreadgroup">Redis Documentation: XREADGROUP</a>
	 */
	default <V> List<ObjectRecord<K, V>> read(@NonNull Class<V> targetType, @NonNull Consumer consumer,
			@NonNull StreamReadOptions readOptions, StreamOffset<@NonNull K> @NonNull... streams) {

		Assert.notNull(targetType, "Target type must not be null");

		return map(read(consumer, readOptions, streams), targetType);
	}

	/**
	 * Read records from a stream within a specific {@link Range} in reverse order.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xrevrange">Redis Documentation: XREVRANGE</a>
	 */
	default List<@NonNull MapRecord<K, HK, HV>> reverseRange(@NonNull K key, @NonNull Range<String> range) {
		return reverseRange(key, range, Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a {@link Limit} in reverse order.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xrevrange">Redis Documentation: XREVRANGE</a>
	 */
	List<@NonNull MapRecord<K, HK, HV>> reverseRange(@NonNull K key, @NonNull Range<String> range, @NonNull Limit limit);

	/**
	 * Read records from a stream within a specific {@link Range} in reverse order as {@link ObjectRecord}.
	 *
	 * @param targetType the target type of the payload.
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xrevrange">Redis Documentation: XREVRANGE</a>
	 */
	default <V> List<@NonNull ObjectRecord<K, V>> reverseRange(@NonNull Class<V> targetType, @NonNull K key,
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
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xrevrange">Redis Documentation: XREVRANGE</a>
	 */
	default <V> List<@NonNull ObjectRecord<K, V>> reverseRange(@NonNull Class<V> targetType, @NonNull K key,
			@NonNull Range<String> range, @NonNull Limit limit) {

		Assert.notNull(targetType, "Target type must not be null");

		return map(reverseRange(key, range, limit), targetType);
	}

	/**
	 * Trims the stream to {@code count} elements.
	 *
	 * @param key the stream key.
	 * @param count length of the stream.
	 * @return number of removed entries. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://redis.io/commands/xtrim">Redis Documentation: XTRIM</a>
	 */
	Long trim(@NonNull K key, long count);

	/**
	 * Trims the stream to {@code count} elements.
	 *
	 * @param key the stream key.
	 * @param count length of the stream.
	 * @param approximateTrimming the trimming must be performed in a approximated way in order to maximize performances.
	 * @return number of removed entries. {@literal null} when used in pipeline / transaction.
	 * @since 2.4
	 * @see <a href="https://redis.io/commands/xtrim">Redis Documentation: XTRIM</a>
	 */
	Long trim(@NonNull K key, long count, boolean approximateTrimming);

	/**
	 * Get the {@link HashMapper} for a specific type.
	 *
	 * @param targetType must not be {@literal null}.
	 * @param <V>
	 * @return the {@link HashMapper} suitable for a given type;
	 */
	@Override
	<V> @NonNull HashMapper<V, HK, HV> getHashMapper(@NonNull Class<V> targetType);

	/**
	 * Map record from {@link MapRecord} to {@link ObjectRecord}.
	 *
	 * @param record the stream record to map.
	 * @param targetType the target type of the payload.
	 * @return the mapped {@link ObjectRecord}.
	 * @since 2.x
	 */
	default <V> ObjectRecord<K, V> map(@NonNull MapRecord<K, HK, HV> record, @NonNull Class<V> targetType) {

		Assert.notNull(record, "Record must not be null");
		Assert.notNull(targetType, "Target type must not be null");

		return StreamObjectMapper.toObjectRecord(record, this, targetType);
	}

	/**
	 * Map records from {@link MapRecord} to {@link ObjectRecord}s.
	 *
	 * @param records the stream records to map.
	 * @param targetType the target type of the payload.
	 * @return the mapped {@link ObjectRecord object records}.
	 * @since 2.x
	 */
	default <V> List<@NonNull ObjectRecord<K, V>> map(@NonNull List<@NonNull MapRecord<K, HK, HV>> records,
			@NonNull Class<V> targetType) {

		Assert.notNull(records, "Records must not be null");
		Assert.notNull(targetType, "Target type must not be null");

		return StreamObjectMapper.toObjectRecords(records, this, targetType);
	}

	/**
	 * Deserialize a {@link ByteRecord} using the configured serializers into a {@link MapRecord}.
	 *
	 * @param record the stream record to map.
	 * @return deserialized {@link MapRecord}.
	 * @since 2.x
	 */
	@NonNull
	MapRecord<K, HK, HV> deserializeRecord(@NonNull ByteRecord record);

}
