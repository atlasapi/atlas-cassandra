package org.atlasapi.media.content;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.atlasapi.media.content.ContentColumn.DESCRIPTION;
import static org.atlasapi.media.content.ContentColumn.IDENTIFICATION;
import static org.atlasapi.media.content.ContentColumn.SOURCE;
import static org.atlasapi.media.content.ContentColumn.TYPE;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.atlasapi.media.common.Id;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.ChildRef;
import org.atlasapi.media.entity.EntityType;
import org.atlasapi.media.entity.Item.ContainerSummary;
import org.atlasapi.media.entity.ParentRef;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.util.Resolved;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
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
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.exceptions.NotFoundException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnQuery;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.query.RowSliceQuery;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;

public final class CassandraContentStore extends AbstractContentStore {
    
    public static final Builder builder(AstyanaxContext<Keyspace> context, 
            String name, ContentHasher hasher, IdGenerator idGenerator) {
        return new Builder(context, name, hasher, idGenerator);
    }
    
    public static final class Builder {

        private final AstyanaxContext<Keyspace> context;
        private final String name;
        private final ContentHasher hasher;
        private final IdGenerator idGenerator;
        
        private ConsistencyLevel readCl = ConsistencyLevel.CL_QUORUM;
        private ConsistencyLevel writeCl = ConsistencyLevel.CL_QUORUM;
        private Clock clock = new SystemClock();

        public Builder(AstyanaxContext<Keyspace> context, String name, 
                       ContentHasher hasher, IdGenerator idGenerator) {
            this.context = context;
            this.name = name;
            this.hasher = hasher;
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
        
        public CassandraContentStore build() {
            return new CassandraContentStore(context, name, readCl, writeCl, 
                hasher, idGenerator, clock);
        }
        
    }

    private final Keyspace keyspace;
    private final ConsistencyLevel readConsistency;
    private final ConsistencyLevel writeConsistency;
    private final ColumnFamily<Long, String> mainCf;
    private final ColumnFamily<String, String> aliasCf;
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ContentMarshaller marshaller = new ProtobufContentMarshaller();
    
    private final Function<Row<Long, String>, Content> rowToContent =
        new Function<Row<Long, String>, Content>() {
            @Override
            public Content apply(Row<Long, String> input) {
                return marshaller.unmarshallCols(input.getColumns());
            }
        };


    public CassandraContentStore(AstyanaxContext<Keyspace> context,
        String cfName, ConsistencyLevel readConsistency, ConsistencyLevel writeConsistency, 
        ContentHasher hasher, IdGenerator idGenerator, Clock clock) {
        super(hasher, idGenerator, clock);
        this.keyspace = checkNotNull(context.getEntity());
        this.readConsistency = checkNotNull(readConsistency);
        this.writeConsistency = checkNotNull(writeConsistency);
        this.mainCf = ColumnFamily.newColumnFamily(checkNotNull(cfName),
            LongSerializer.get(), StringSerializer.get());
        this.aliasCf = ColumnFamily.newColumnFamily(cfName+"_aliases", 
            StringSerializer.get(), StringSerializer.get());
    }
    
    @Override
    public Resolved<Content> resolveIds(Iterable<Id> ids) {
        try {
            Iterable<Long> longIds = Iterables.transform(ids, Id.toLongValue());
            OperationResult<Rows<Long, String>> opResult = resolveLongs(longIds);
            log.debug("{} resolve content: {}", Thread.currentThread().getId(), opResult.getLatency(TimeUnit.MILLISECONDS));
            Rows<Long, String> rows = opResult.getResult();
            return Resolved.valueOf(FluentIterable.from(rows).transform(rowToContent));
        } catch (Exception e) {
            throw new CassandraPersistenceException(Joiner.on(", ").join(ids), e);
        }
    }

    private OperationResult<Rows<Long, String>> resolveLongs(Iterable<Long> longIds) throws ConnectionException {
        return keyspace
            .prepareQuery(mainCf)
            .setConsistencyLevel(readConsistency)
            .getKeySlice(longIds)
            .execute();
    }
    
    @Override
    public OptionalMap<String, Content> resolveAliases(Iterable<String> aliases, Publisher source) {
        try {
            Set<String> uniqueAliases = ImmutableSet.copyOf(aliases);
            List<Long> ids = resolveIdsForAliases(source, uniqueAliases);
            Rows<Long,String> resolved = resolveLongs(ids).getResult();
            Iterable<Content> contents = Iterables.transform(resolved, rowToContent);
            ImmutableMap.Builder<String, Optional<Content>> aliasMap = ImmutableMap.builder();
            for (Content content : contents) {
                for (String alias : content.getAliases()) {
                    if (uniqueAliases.contains(alias)) {
                        aliasMap.put(alias, Optional.of(content));
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
    protected void doWriteContent(Content content) {
        try {
            long id = content.getId().longValue();
            MutationBatch batch = keyspace.prepareMutationBatch();
            batch.setConsistencyLevel(writeConsistency);
            marshaller.marshallInto(batch.withRow(mainCf, id), content);
            for (String alias : content.getAliases()) {
                batch.withRow(aliasCf, alias)
                    .putColumn(content.getPublisher().key(), id);
            }
            batch.execute();
        } catch (Exception e) {
            throw new CassandraPersistenceException(content.toString(), e);
        }
    }

    @Override
    @Nullable
    protected Content resolveAlias(String alias, Publisher source) {
        try {
            ColumnQuery<String> query = keyspace.prepareQuery(aliasCf)
                .getKey(alias)
                .getColumn(source.key());
            Column<String> idCol = query.execute().getResult();
            long id = idCol.getLongValue();
            return resolve(id, null);
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    @Nullable
    protected Content resolveId(Id id) {
        return resolve(id.longValue(), null);
    }

    private Content resolve(long longId, Set<ContentColumn> colNames) {
        try {
            RowQuery<Long, String> query = keyspace.prepareQuery(mainCf)
                .getKey(longId);
            if (colNames != null && colNames.size() > 0) {
                query = query.withColumnSlice(Collections2.transform(colNames, Functions.toStringFunction()));
            }
            ColumnList<String> cols = query.execute().getResult();
            
            return cols.size() > 0 ? marshaller.unmarshallCols(cols)
                                   : null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected ContainerSummary summarize(ParentRef id) {
        return summarize((Container)resolve(id.getId().longValue(), 
            ImmutableSet.of(TYPE, SOURCE, IDENTIFICATION, DESCRIPTION)));
    }

    private ContainerSummary summarize(Container container) {
        ContainerSummary summary = null;
        if (container != null) {
            summary = container.accept(new ContainerVisitor<ContainerSummary>() {

                @Override
                public ContainerSummary visit(Brand brand) {
                    return new ContainerSummary(
                        EntityType.from(brand).name(), brand.getTitle(), 
                        brand.getDescription(), null);
                }

                @Override
                public ContainerSummary visit(Series series) {
                    return new ContainerSummary(
                        EntityType.from(series).name(), series.getTitle(), 
                        series.getDescription(), series.getSeriesNumber());
                }
                
            });
        }
        return summary;
    }

    @Override
    protected void writeSecondaryContainerRef(ParentRef primary, ChildRef seriesRef) {
        try {
            Long rowId = primary.getId().longValue();
            Brand container = new Brand();
            container.setSeriesRefs(ImmutableList.of(seriesRef));
            container.setThisOrChildLastUpdated(seriesRef.getUpdated());
            
            MutationBatch batch = keyspace.prepareMutationBatch();
            batch.setConsistencyLevel(writeConsistency);
            ColumnListMutation<String> mutation = batch.withRow(mainCf, rowId);
            marshaller.marshallInto(mutation, container);
            batch.execute();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected void writeChildRef(ParentRef containerRef, ChildRef childRef) {
        try {

            Long rowId = containerRef.getId().longValue();
            Container container = new Brand();
            container.setChildRefs(ImmutableList.of(childRef));
            container.setThisOrChildLastUpdated(childRef.getUpdated());
            
            MutationBatch batch = keyspace.prepareMutationBatch();
            batch.setConsistencyLevel(writeConsistency);
            ColumnListMutation<String> mutation = batch.withRow(mainCf, rowId);
            marshaller.marshallInto(mutation, container);
            batch.execute();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}
