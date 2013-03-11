package org.atlasapi.equiv;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.List;

import org.atlasapi.equiv.CassandraEquivalenceRecordStore;
import org.atlasapi.equiv.EquivalenceRecord;
import org.atlasapi.equiv.EquivalenceRecordStore;
import org.atlasapi.equiv.EquivalenceRef;
import org.atlasapi.media.common.CassandraHelper;
import org.atlasapi.media.common.Id;
import org.atlasapi.media.content.Content;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Publisher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.metabroadcast.common.collect.OptionalMap;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;

public class CassandraEquivalenceRecordStoreIT {

    private static final String CF_NAME = "equivalence";
    private static final AstyanaxContext<Keyspace> context =
            CassandraHelper.testCassandraContext();

    private final EquivalenceRecordStore store = new CassandraEquivalenceRecordStore(
            context, CF_NAME, ConsistencyLevel.CL_ONE, ConsistencyLevel.CL_ONE);

    @BeforeClass
    public static void setup() throws ConnectionException {
        context.start();
        CassandraHelper.createKeyspace(context);
        CassandraHelper.createColumnFamily(context,
                CF_NAME, LongSerializer.get(), StringSerializer.get());
    }
    

    @AfterClass
    public static void tearDown() throws ConnectionException {
        context.getEntity().dropKeyspace();
    }

    @After
    public void clearCf() throws ConnectionException {
        CassandraHelper.clearColumnFamily(context, CF_NAME);
    }

    @Test
    public void test() {
        EquivalenceRecord record1 = record(1234L, Publisher.METABROADCAST)
                .copyWithGeneratedAdjacent(ImmutableList.of(ref(1235, Publisher.BBC)))
                .copyWithExplicitAdjacent(ImmutableList.of(ref(1236, Publisher.PA)))
                .copyWithEquivalents(ImmutableList.of(ref(1237, Publisher.C4)));
        EquivalenceRecord record2 = record(1235L, Publisher.BBC)
                .copyWithGeneratedAdjacent(ImmutableList.of(ref(1234, Publisher.METABROADCAST)))
                .copyWithExplicitAdjacent(ImmutableList.of(ref(1236, Publisher.PA)))
                .copyWithEquivalents(ImmutableList.of(ref(1237, Publisher.C4)));
        
        store.writeRecords(ImmutableList.of(record1, record2));
        
        List<Id> ids = Lists.transform(
            ImmutableList.of(1234L, 1235L, 1236L, 1237L), 
            Id.fromLongValue()
        );
        OptionalMap<Id, EquivalenceRecord> resolved = store.resolveRecords(ids);
        
        assertThat(resolved.get(ids.get(0)).get(), is(record1));
        assertThat(resolved.get(ids.get(1)).get(), is(record2));
        assertThat(resolved.get(ids.get(2)).isPresent(), is(false));
        assertThat(resolved.get(ids.get(3)).isPresent(), is(false));
    }
    
    private EquivalenceRecord record(long id, Publisher source) {
        Content content = new Episode();
        content.setId(id);
        content.setPublisher(source);
        
        EquivalenceRecord record = EquivalenceRecord.valueOf(content);
        return record;
    }

    private EquivalenceRef ref(int id, Publisher source) {
        return new EquivalenceRef(Id.valueOf(id), source);
    }

}
