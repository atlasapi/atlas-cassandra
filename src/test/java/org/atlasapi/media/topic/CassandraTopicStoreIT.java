package org.atlasapi.media.topic;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.atlasapi.media.common.CassandraHelper;
import org.atlasapi.media.common.Id;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.topic.Topic.Type;
import org.atlasapi.media.util.Resolved;
import org.atlasapi.media.util.WriteResult;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.metabroadcast.common.collect.OptionalMap;
import com.metabroadcast.common.ids.IdGenerator;
import com.metabroadcast.common.time.Clock;
import com.metabroadcast.common.time.DateTimeZones;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;

public class CassandraTopicStoreIT {
    
    public class StubbableEquivalence<T> extends Equivalence<T> {

        @Override
        public boolean doEquivalent(T a, T b) {
            return false;
        }

        @Override
        protected int doHash(T t) {
            return 0;
        }

    }

    private static final AstyanaxContext<Keyspace> context =
        CassandraHelper.testCassandraContext();

    @SuppressWarnings("unchecked")
    private final StubbableEquivalence<Topic> equiv = mock(StubbableEquivalence.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final Clock clock = mock(Clock.class);

    private final CassandraTopicStore topicStore = CassandraTopicStore
        .builder(context, "topics", equiv, idGenerator)
        .withReadConsistency(ConsistencyLevel.CL_ONE)
        .withWriteConsistency(ConsistencyLevel.CL_ONE)
        .withClock(clock)
        .build();

    @BeforeClass
    public static void setup() throws ConnectionException {
        context.start();
        CassandraHelper.createKeyspace(context);
        CassandraHelper.createColumnFamily(context,
            "topics",
            LongSerializer.get(),
            StringSerializer.get());
        CassandraHelper.createColumnFamily(context,
            "topics_aliases",
            StringSerializer.get(),
            StringSerializer.get());
    }

    @AfterClass
    public static void tearDown() throws ConnectionException {
        context.getEntity().dropKeyspace();
    }

    @After
    public void clearCf() throws ConnectionException {
        CassandraHelper.clearColumnFamily(context, "Content");
        CassandraHelper.clearColumnFamily(context, "Content_aliases");
    }

    @Test
    public void testResolveIds() {
        Topic topic1 = new Topic();
        topic1.setPublisher(Publisher.DBPEDIA);
        topic1.addAlias("dbpedia");
        topic1.setType(Type.UNKNOWN);

        Topic topic2 = new Topic();
        topic2.setPublisher(Publisher.METABROADCAST);
        topic2.addAlias("mbst");
        topic2.setType(Type.UNKNOWN);

        DateTime now = new DateTime(DateTimeZones.UTC);
        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L, 1235L);

        WriteResult<Topic> topic1result = topicStore.writeTopic(topic1);
        WriteResult<Topic> topic2result = topicStore.writeTopic(topic2);

        assertThat(topic1result.written(), is(true));
        assertThat(topic2result.written(), is(true));

        verify(equiv, never()).equivalent(any(Topic.class), any(Topic.class));

        Id topic1id = topic1result.getResource().getId();
        Id topic2id = topic2result.getResource().getId();

        Resolved<Topic> resolved = topicStore.resolveIds(ImmutableList.of(
            topic1id, topic2id
            ));

        OptionalMap<Id, Topic> resolvedMap = resolved.toMap();
        assertThat(resolvedMap.get(topic1id).get().getAliases(), hasItem("dbpedia"));
        assertThat(resolvedMap.get(topic2id).get().getAliases(), hasItem("mbst"));
    }

    @Test
    public void testResolveAliases() {
        Topic topic1 = new Topic();
        topic1.setPublisher(Publisher.DBPEDIA);
        topic1.addAlias("shared");
        topic1.setType(Type.UNKNOWN);

        Topic topic2 = new Topic();
        topic2.setPublisher(Publisher.METABROADCAST);
        topic2.addAlias("shared");
        topic2.setType(Type.UNKNOWN);

        DateTime now = new DateTime(DateTimeZones.UTC);
        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L, 1235L);

        topicStore.writeTopic(topic1);
        topicStore.writeTopic(topic2);

        OptionalMap<String, Topic> resolved = topicStore.resolveAliases(
            ImmutableList.of("shared"), Publisher.METABROADCAST);
        assertThat(resolved.size(), is(1));
        Topic topic = resolved.get("shared").get();
        assertThat(topic.getPublisher(), is(Publisher.METABROADCAST));
        assertThat(topic.getId(), is(Id.valueOf(1235)));

        resolved = topicStore.resolveAliases(
            ImmutableList.of("shared"), Publisher.DBPEDIA);
        assertThat(resolved.size(), is(1));
        topic = resolved.get("shared").get();
        assertThat(topic.getPublisher(), is(Publisher.DBPEDIA));
        assertThat(topic.getId(), is(Id.valueOf(1234)));

    }

    @Test
    public void testDoesntRewriteTopicWhenEquivalentToPrevious() {
        Topic topic1 = new Topic();
        topic1.setPublisher(Publisher.DBPEDIA);
        topic1.addAlias("shared");
        topic1.setType(Type.UNKNOWN);

        DateTime now = new DateTime(DateTimeZones.UTC);
        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L, 1235L);

        WriteResult<Topic> writeResult = topicStore.writeTopic(topic1);

        reset(clock, idGenerator, equiv);
        when(equiv.doEquivalent(any(Topic.class), any(Topic.class)))
            .thenReturn(true);

        WriteResult<Topic> writeResult2 = topicStore.writeTopic(writeResult.getResource());

        assertThat(writeResult2.written(), is(false));
        
        verify(idGenerator, never()).generateRaw();
        verify(clock, never()).now();
        verify(equiv).doEquivalent(any(Topic.class), any(Topic.class));
    }

}
