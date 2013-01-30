package org.atlasapi.media.topic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.atlasapi.media.common.Id;
import org.atlasapi.media.content.CassandraPersistenceException;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.util.Resolved;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.metabroadcast.common.collect.ImmutableOptionalMap;
import com.metabroadcast.common.collect.OptionalMap;
import com.metabroadcast.common.ids.IdGenerator;
import com.metabroadcast.common.time.Clock;
import com.metabroadcast.common.time.SystemClock;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.exceptions.NotFoundException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnQuery;
import com.netflix.astyanax.query.RowSliceQuery;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;


public class CassandraTopicStore extends AbstractTopicStore {

    public static final Builder builder(AstyanaxContext<Keyspace> context, 
            String name, Equivalence<? super Topic> equivalence, IdGenerator idGenerator) {
        return new Builder(context, name, equivalence, idGenerator);
    }
    
    public static final class Builder {

        private final AstyanaxContext<Keyspace> context;
        private final String name;
        private final Equivalence<? super Topic> equivalence;
        private final IdGenerator idGenerator;
        
        private ConsistencyLevel readCl = ConsistencyLevel.CL_QUORUM;
        private ConsistencyLevel writeCl = ConsistencyLevel.CL_QUORUM;
        private Clock clock = new SystemClock();

        public Builder(AstyanaxContext<Keyspace> context, String name,
            Equivalence<? super Topic> equivalence, IdGenerator idGenerator) {
                this.context = context;
                this.name = name;
                this.equivalence = equivalence;
                this.idGenerator = idGenerator;
        }
        
        public Builder withReadConsistency(ConsistencyLevel readCl) {
            this.readCl = readCl;
            return this;
        }
        
        public Builder withWriteConsistency(ConsistencyLevel writeCl) {
            this.writeCl = writeCl;
            return this;
        }
        
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }
        
        public CassandraTopicStore build() {
            return new CassandraTopicStore(context, name, readCl, writeCl, 
                equivalence, idGenerator, clock);
        }
        
    }
    
    private final Keyspace keyspace;
    private final ConsistencyLevel readConsistency;
    private final ConsistencyLevel writeConsistency;
    private final ColumnFamily<Long, String> mainCf;
    private final ColumnFamily<String, String> aliasCf;
    
    private final String valueColumn = "topic";
    private final TopicSerializer topicSerializer = new TopicSerializer();
    private final Function<Row<Long, String>, Topic> rowToTopic =
        new Function<Row<Long, String>, Topic>() {
            @Override
            public Topic apply(Row<Long, String> input) {
                return topicSerializer.deserialize(input.getColumns().getColumnByName(valueColumn).getByteArrayValue());
            }
        };

    public CassandraTopicStore(AstyanaxContext<Keyspace> context, String cfName,
        ConsistencyLevel readCl, ConsistencyLevel writeCl, Equivalence<? super Topic> equivalence,
        IdGenerator idGenerator, Clock clock) {
        super(idGenerator, equivalence, clock);
        this.keyspace = checkNotNull(context.getEntity());
        this.readConsistency = checkNotNull(readCl);
        this.writeConsistency = checkNotNull(writeCl);
        this.mainCf = ColumnFamily.newColumnFamily(checkNotNull(cfName),
            LongSerializer.get(), StringSerializer.get());
        this.aliasCf = ColumnFamily.newColumnFamily(cfName+"_aliases", 
            StringSerializer.get(), StringSerializer.get());
    }

    @Override
    public Resolved<Topic> resolveIds(Iterable<Id> ids) {
        try {
            Iterable<Long> longIds = Iterables.transform(ids, Id.toLongValue());
            Rows<Long, String> rows = resolveLongs(longIds);
            return Resolved.valueOf(FluentIterable.from(rows).transform(rowToTopic));
        } catch (Exception e) {
            throw new CassandraPersistenceException(Joiner.on(", ").join(ids), e);
        }
    }

    private Rows<Long, String> resolveLongs(Iterable<Long> longIds) throws ConnectionException {
        return keyspace
            .prepareQuery(mainCf)
            .setConsistencyLevel(readConsistency)
            .getKeySlice(longIds)
            .execute()
            .getResult();
    }

    @Override
    public OptionalMap<String, Topic> resolveAliases(Iterable<String> aliases, Publisher source) {
        try {
            Set<String> uniqueAliases = ImmutableSet.copyOf(aliases);
            List<Long> ids = resolveIdsForAliases(source, uniqueAliases);
            Rows<Long,String> resolved = resolveLongs(ids);
            Iterable<Topic> topics = Iterables.transform(resolved, rowToTopic);
            ImmutableMap.Builder<String, Optional<Topic>> aliasMap = ImmutableMap.builder();
            for (Topic topic : topics) {
                for (String alias : topic.getAliases()) {
                    if (uniqueAliases.contains(alias)) {
                        aliasMap.put(alias, Optional.of(topic));
                    }
                }
            }
            return ImmutableOptionalMap.copyOf(aliasMap.build());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    private List<Long> resolveIdsForAliases(Publisher source, Set<String> uniqueAliases)
        throws ConnectionException {
        String columnName = source.key();
        RowSliceQuery<String, String> aliasQuery = keyspace.prepareQuery(aliasCf)
            .getRowSlice(uniqueAliases)
            .withColumnSlice(columnName);
        Rows<String,String> rows = aliasQuery.execute().getResult();
        List<Long> ids = Lists.newArrayListWithCapacity(rows.size());
        for (Row<String, String> row : rows) {
            Column<String> idCell = row.getColumns().getColumnByName(columnName);
            if (idCell != null) {
                ids.add(idCell.getLongValue());
            }
        }
        return ids;
    }

    @Override
    protected void doWrite(Topic topic) {
        try {
            long id = topic.getId().longValue();
            MutationBatch batch = keyspace.prepareMutationBatch();
            batch.setConsistencyLevel(writeConsistency);
            batch.withRow(mainCf, id)
                .putColumn(valueColumn, topicSerializer.serialize(topic));
            for (String alias : topic.getAliases()) {
                batch.withRow(aliasCf, alias)
                    .putColumn(topic.getPublisher().key(), id);
            }
            batch.execute();
        } catch (Exception e) {
            throw new CassandraPersistenceException(topic.toString(), e);
        }
    }

    @Override
    @Nullable
    protected Topic doResolveId(Id id) {
        return resolve(id.longValue());
    }

    @Override
    @Nullable
    protected Topic doResolveAlias(String alias, Publisher source) {
        try {
            ColumnQuery<String> query = keyspace.prepareQuery(aliasCf)
                .getKey(alias).getColumn(source.key());
            Column<String> idCol = query.execute().getResult();
            return resolve(idCol.getLongValue());
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Topic resolve(long longId) {
        try {
            ColumnQuery<String> query = keyspace.prepareQuery(mainCf)
                .getKey(longId).getColumn(valueColumn);
            Column<String> col = query.execute().getResult();
            
            return topicSerializer.deserialize(col.getByteArrayValue());
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
}
