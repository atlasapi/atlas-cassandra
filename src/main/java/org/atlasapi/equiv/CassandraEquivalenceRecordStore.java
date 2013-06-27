package org.atlasapi.equiv;

import static com.google.common.base.Preconditions.checkNotNull;

import org.atlasapi.equiv.EquivalenceRecord;
import org.atlasapi.equiv.EquivalenceRecordStore;
import org.atlasapi.media.common.Id;
import org.atlasapi.media.common.Serializer;
import org.atlasapi.media.content.CassandraPersistenceException;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.collect.ImmutableOptionalMap;
import com.metabroadcast.common.collect.OptionalMap;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;

public class CassandraEquivalenceRecordStore implements EquivalenceRecordStore {

    private static final String RECORD_COLUMN = "record";
    
    private final Keyspace keyspace;
    private final ConsistencyLevel readConsistency;
    private final ConsistencyLevel writeConsistency;
    private final ColumnFamily<Long, String> columnFamily;
    
    private final Serializer<EquivalenceRecord, byte[]> serializer
        = new EquivalenceRecordSerializer();
    
    public CassandraEquivalenceRecordStore(AstyanaxContext<Keyspace> context, String cfName,
            ConsistencyLevel readConsistency, ConsistencyLevel writeConsistency) {
        this.keyspace = checkNotNull(context.getClient());
        this.readConsistency = checkNotNull(readConsistency);
        this.writeConsistency = checkNotNull(writeConsistency);
        this.columnFamily = ColumnFamily.newColumnFamily(cfName, 
                LongSerializer.get(), StringSerializer.get());
    }

    @Override
    public void writeRecords(Iterable<EquivalenceRecord> records) {
        try {
            MutationBatch batch = keyspace.prepareMutationBatch()
                .setConsistencyLevel(writeConsistency);
            for (EquivalenceRecord record : records) {
                long id = record.getId().longValue();
                batch.withRow(columnFamily, id)
                    .putColumn(RECORD_COLUMN, serializer.serialize(record));
            }
            batch.execute();
        } catch (Exception e) {
            throw new CassandraPersistenceException(records.toString(), e);
        }
    }

    @Override
    public OptionalMap<Id, EquivalenceRecord> resolveRecords(Iterable<Id> ids) {
        try {
            Rows<Long, String> rows = keyspace.prepareQuery(columnFamily)
                .setConsistencyLevel(readConsistency)
                .getKeySlice(Iterables.transform(ids, Id.toLongValue()))
                .execute()
                .getResult();
            
            ImmutableMap.Builder<Id, Optional<EquivalenceRecord>> recordMap
                = ImmutableMap.builder();
            for (Id id : ids) {
                Row<Long, String> row = rows.getRow(id.longValue());
                Column<String> recordCol = row.getColumns().getColumnByName(RECORD_COLUMN);
                if (recordCol != null) {
                    EquivalenceRecord record = serializer.deserialize(recordCol.getByteArrayValue());
                    recordMap.put(id, Optional.of(record));
                }
            }
            return ImmutableOptionalMap.copyOf(recordMap.build());
        } catch (ConnectionException e) {
            throw new CassandraPersistenceException(Joiner.on(", ").join(ids), e);
        }
    }
    
}
