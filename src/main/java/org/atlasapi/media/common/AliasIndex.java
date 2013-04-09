package org.atlasapi.media.common;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.Publisher;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.RowSliceQuery;

public final class AliasIndex {

    private final Keyspace keyspace;
    private final ColumnFamily<String, String> columnFamily;

    public AliasIndex(Keyspace keyspace, ColumnFamily<String, String> columnFamily) {
        this.keyspace = checkNotNull(keyspace);
        this.columnFamily = checkNotNull(columnFamily);
    }
    
    private final String serialize(Alias alias) {
        return alias.getNamespace() + ":" + alias.getValue();
    }
    
    private List<String> serialize(List<Alias> uniqueAliases) {
        return Lists.transform(uniqueAliases, new Function<Alias, String>() {
            @Override
            public String apply(Alias input) {
                return serialize(input);
            }
        });
    }
    
    public MutationBatch writeAliases(MutationBatch batch, long id, Publisher source, Iterable<Alias> aliases) {
        checkNotNull(batch);
        checkNotNull(source);
        for(Alias alias : checkNotNull(aliases)) {
            batch.withRow(columnFamily, serialize(checkNotNull(alias))).putColumn(source.key(), id);
        }
        return batch;
    }
    
    public Set<Long> readAliases(Publisher source, Iterable<Alias> aliases) throws ConnectionException {
        ImmutableSet<Alias> uniqueAliases = ImmutableSet.copyOf(aliases);
        String columnName = checkNotNull(source).key();
        RowSliceQuery<String, String> aliasQuery = keyspace.prepareQuery(columnFamily)
            .getRowSlice(serialize(uniqueAliases.asList()))
            .withColumnSlice(columnName);
        Rows<String,String> rows = aliasQuery.execute().getResult();
        
        List<Long> ids = Lists.newArrayListWithCapacity(rows.size());
        for (Row<String, String> row : rows) {
            Column<String> idCell = row.getColumns().getColumnByName(columnName);
            if (idCell != null) {
                ids.add(idCell.getLongValue());
            }
        }
        return ImmutableSet.copyOf(ids);
    }

    
}
