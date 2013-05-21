package org.atlasapi.media.content.schedule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.atlasapi.media.channel.Channel;
import org.atlasapi.media.common.CassandraHelper;
import org.atlasapi.media.common.Id;
import org.atlasapi.media.content.CassandraContentStore;
import org.atlasapi.media.content.Content;
import org.atlasapi.media.content.ContentHasher;
import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.ChannelSchedule;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Schedule;
import org.atlasapi.media.entity.Version;
import org.atlasapi.media.util.ItemAndBroadcast;
import org.atlasapi.media.util.Resolved;
import org.atlasapi.media.util.WriteResult;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.metabroadcast.common.ids.SequenceGenerator;
import com.metabroadcast.common.time.Clock;
import com.metabroadcast.common.time.DateTimeZones;
import com.metabroadcast.common.time.TimeMachine;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;

public class CassandraScheduleStoreIT {

    private static final String SCHEDULE_CF_NAME = "schedule";
    private static final String CONTENT_CF_NAME = "content";
    private static final String CONTENT_ALIASES_CF_NAME = "content_aliases";

    private static final AstyanaxContext<Keyspace> context =
            CassandraHelper.testCassandraContext();

    //hasher is mock till we have a non-Mongo based one.
    private final ContentHasher hasher = mock(ContentHasher.class);
    
    private final Clock clock = new TimeMachine();
    private final CassandraContentStore contentStore = CassandraContentStore
            .builder(context, CONTENT_CF_NAME, hasher, new SequenceGenerator())
            .withReadConsistency(ConsistencyLevel.CL_ONE)
            .withWriteConsistency(ConsistencyLevel.CL_ONE)
            .withClock(clock)
            .build();
    
    private final CassandraScheduleStore store = CassandraScheduleStore
            .builder(context, SCHEDULE_CF_NAME, contentStore)
            .withReadConsistency(ConsistencyLevel.CL_ONE)
            .withWriteConsistency(ConsistencyLevel.CL_ONE)
            .withClock(clock)
            .build();
    
    private final Publisher source = Publisher.METABROADCAST;
    private final Channel channel = Channel.builder().build();

    @BeforeClass
    public static void setup() throws ConnectionException {
        context.start();
        CassandraHelper.createKeyspace(context);
        CassandraHelper.createColumnFamily(context,
                SCHEDULE_CF_NAME,
                StringSerializer.get(),
                StringSerializer.get());
        CassandraHelper.createColumnFamily(context, CONTENT_CF_NAME, LongSerializer.get(), StringSerializer.get());
        CassandraHelper.createColumnFamily(context, CONTENT_ALIASES_CF_NAME, StringSerializer.get(), StringSerializer.get(), LongSerializer.get());
    }

    @AfterClass
    public static void tearDown() throws ConnectionException {
        context.getEntity().dropKeyspace();
    }
    
    @Before
    public void setUp() {
        channel.setCanonicalUri("channel");
    }

    @After
    public void clearCf() throws ConnectionException {
        context.getEntity().truncateColumnFamily(SCHEDULE_CF_NAME);
        context.getEntity().truncateColumnFamily(CONTENT_CF_NAME);
        context.getEntity().truncateColumnFamily(CONTENT_ALIASES_CF_NAME);
    }

    @Test
    public void testWritingAndResolvingANewSchedule() throws Exception {
        
        DateTime start = new DateTime(2013,05,31,14,0,0,0,DateTimeZones.LONDON);
        DateTime middle = new DateTime(2013,05,31,23,0,0,0,DateTimeZones.LONDON);
        DateTime end = new DateTime(2013,06,01,14,0,0,0,DateTimeZones.LONDON);
        
        ImmutableList<ScheduleHierarchy> hiers = ImmutableList.<ScheduleHierarchy>of(
            ScheduleHierarchy.itemOnly(itemAndBroadcast(null, "one", source, channel, start, middle)), 
            ScheduleHierarchy.itemOnly(itemAndBroadcast(null, "two", source, channel, middle, end))
        );
        
        Interval writtenInterval = new Interval(start, end);
        List<WriteResult<? extends Content>> results = store.writeSchedule(hiers, channel, writtenInterval);
        
        assertThat(results.size(), is(2));
        
        Interval requestedInterval = new Interval(
            new DateTime(2013,05,31,10,0,0,0,DateTimeZones.UTC), 
            new DateTime(2013,05,31,22,30,0,0,DateTimeZones.UTC) 
        );
        Schedule schedule = future(store.resolve(ImmutableList.of(channel), requestedInterval, source));
        ChannelSchedule channelSchedule = Iterables.getOnlyElement(schedule.channelSchedules());
        
        assertThat(channelSchedule.getChannel(), is(channel));
        assertThat(channelSchedule.getInterval(), is(requestedInterval));
        assertThat(channelSchedule.getEntries().size(), is(2));
        assertThat(channelSchedule.getEntries().get(0).getItem().getId().longValue(), is(1L));
        assertThat(channelSchedule.getEntries().get(1).getItem().getId().longValue(), is(2L));
    }

    @Test
    public void testReWritingASchedule() throws Exception {

        DateTime start = new DateTime(2013,05,31,14,0,0,0,DateTimeZones.LONDON);
        DateTime middle = new DateTime(2013,05,31,23,0,0,0,DateTimeZones.LONDON);
        DateTime end = new DateTime(2013,06,01,14,0,0,0,DateTimeZones.LONDON);
        
        ItemAndBroadcast episode1 = itemAndBroadcast(null, "one", source, channel, start, middle);
        ItemAndBroadcast episode2 = itemAndBroadcast(null, "two", source, channel, middle, end);
        
        ImmutableList<ScheduleHierarchy> hiers = ImmutableList.<ScheduleHierarchy>of(
            ScheduleHierarchy.itemOnly(episode1), 
            ScheduleHierarchy.itemOnly(episode2)
        );
        
        Interval writtenInterval = new Interval(start, end);
        List<WriteResult<? extends Content>> results = store.writeSchedule(hiers, channel, writtenInterval);
        assertThat(results.size(), is(2));

        DateTime newMiddle = new DateTime(2013,05,31,23,30,0,0,DateTimeZones.LONDON);
        
        //items 1 and 2 change 
        when(hasher.hash(argThat(isA(Content.class))))
            .thenReturn("differentOne")
            .thenReturn("oneDifferent")
            .thenReturn("differentTwo")
            .thenReturn("twoDifferent");
        
        episode1 = itemAndBroadcast(null, "one", source, channel, start, newMiddle);
        ItemAndBroadcast episode3 = itemAndBroadcast(null, "three", source, channel, newMiddle, end);
        hiers = ImmutableList.<ScheduleHierarchy>of(
            ScheduleHierarchy.itemOnly(episode1), 
            ScheduleHierarchy.itemOnly(episode3)
        );
        
        results = store.writeSchedule(hiers, channel, writtenInterval);
        assertThat(results.size(), is(2));
        assertTrue(Iterables.all(results, WriteResult.<Content>writtenFilter()));
        
        Interval requestedInterval = new Interval(
            new DateTime(2013,05,31,10,0,0,0,DateTimeZones.UTC), 
            new DateTime(2013,05,31,22,40,0,0,DateTimeZones.UTC) 
        );
        Schedule schedule = future(store.resolve(ImmutableList.of(channel), requestedInterval, source));
        ChannelSchedule channelSchedule = Iterables.getOnlyElement(schedule.channelSchedules());
        
        assertThat(channelSchedule.getChannel(), is(channel));
        assertThat(channelSchedule.getInterval(), is(requestedInterval));
        assertThat(channelSchedule.getEntries().size(), is(2));
        assertThat(channelSchedule.getEntries().get(0).getItem().getId().longValue(), is(1L));
        assertThat(channelSchedule.getEntries().get(1).getItem().getId().longValue(), is(3L));
        
        Resolved<Content> resolved = future(contentStore.resolveIds(ImmutableList.of(Id.valueOf(2))));
        Item two = (Item) resolved.getResources().first().get();
        assertFalse(Iterables.getOnlyElement(Iterables.getOnlyElement(two.getVersions()).getBroadcasts()).isActivelyPublished());
    }
    
    @Test
    public void testReWritingScheduleUpdatesOverlappingBroadcastsInDifferentSegment() throws Exception {

        DateTime start = new DateTime(2013,05,31,14,0,0,0,DateTimeZones.LONDON);
        DateTime middle = new DateTime(2013,05,31,20,0,0,0,DateTimeZones.LONDON);
        DateTime end = new DateTime(2013,06,01,14,0,0,0,DateTimeZones.LONDON);
        
        ItemAndBroadcast episode1 = itemAndBroadcast(null, "one", source, channel, start, middle);
        ItemAndBroadcast episode2 = itemAndBroadcast(null, "two", source, channel, middle, end);
        
        ImmutableList<ScheduleHierarchy> hiers = ImmutableList.<ScheduleHierarchy>of(
            ScheduleHierarchy.itemOnly(episode1), 
            ScheduleHierarchy.itemOnly(episode2)
        );
        
        Interval writtenInterval = new Interval(start, end);
        List<WriteResult<? extends Content>> results = store.writeSchedule(hiers, channel, writtenInterval);
        assertThat(results.size(), is(2));

        DateTime newEnd = new DateTime(2013,05,31,23,30,0,0,DateTimeZones.LONDON);
        
        //items 1 and 2 change 
        when(hasher.hash(argThat(isA(Content.class))))
            .thenReturn("differentOne")
            .thenReturn("oneDifferent")
            .thenReturn("differentTwo")
            .thenReturn("twoDifferent");
        
        episode1 = itemAndBroadcast(null, "one", source, channel, start, middle);
        ItemAndBroadcast episode3 = itemAndBroadcast(null, "three", source, channel, middle, newEnd);
        hiers = ImmutableList.<ScheduleHierarchy>of(
            ScheduleHierarchy.itemOnly(episode1), 
            ScheduleHierarchy.itemOnly(episode3)
        );
        
        results = store.writeSchedule(hiers, channel, writtenInterval);
        assertThat(results.size(), is(2));
        assertTrue(Iterables.all(results, WriteResult.<Content>writtenFilter()));
        
        Interval requestedInterval = new Interval(
            new DateTime(2013,05,31,10,0,0,0,DateTimeZones.UTC), 
            new DateTime(2013,05,31,22,40,0,0,DateTimeZones.UTC) 
        );
        Schedule schedule = future(store.resolve(ImmutableList.of(channel), requestedInterval, source));
        ChannelSchedule channelSchedule = Iterables.getOnlyElement(schedule.channelSchedules());
        
        assertThat(channelSchedule.getChannel(), is(channel));
        assertThat(channelSchedule.getInterval(), is(requestedInterval));
        assertThat(channelSchedule.getEntries().size(), is(2));
        assertThat(channelSchedule.getEntries().get(0).getItem().getId().longValue(), is(1L));
        assertThat(channelSchedule.getEntries().get(1).getItem().getId().longValue(), is(3L));
        
        requestedInterval = new Interval(
            new DateTime(2013,05,31,23,0,0,0,DateTimeZones.UTC), 
            new DateTime(2013,06,01,22,40,0,0,DateTimeZones.UTC) 
        );
        schedule = future(store.resolve(ImmutableList.of(channel), requestedInterval, source));
        channelSchedule = Iterables.getOnlyElement(schedule.channelSchedules());
        
        assertThat(channelSchedule.getChannel(), is(channel));
        assertThat(channelSchedule.getInterval(), is(requestedInterval));
        assertThat(channelSchedule.getEntries().size(), is(0));
        
        Resolved<Content> resolved = future(contentStore.resolveIds(ImmutableList.of(Id.valueOf(2))));
        Item two = (Item) resolved.getResources().first().get();
        assertFalse(Iterables.getOnlyElement(Iterables.getOnlyElement(two.getVersions()).getBroadcasts()).isActivelyPublished());
    }

    @Test
    @Ignore
    // Known issue: if the update interval doesn't cover a now stale broadcast
    // in a subsequent segment then that broadcast won't be updated in the
    // subsequent segment, it will be updated in the store though.
    public void testReWritingScheduleUpdatesOverlappingBroadcastsInDifferentSegmentEvenWhenUpdateIntervalDoesntCoverSegment() throws Exception {
        
        DateTime start = new DateTime(2013,05,31,14,0,0,0,DateTimeZones.LONDON);
        DateTime middle = new DateTime(2013,05,31,20,0,0,0,DateTimeZones.LONDON);
        DateTime end = new DateTime(2013,06,01,14,0,0,0,DateTimeZones.LONDON);
        
        ItemAndBroadcast episode1 = itemAndBroadcast(null, "one", source, channel, start, middle);
        ItemAndBroadcast episode2 = itemAndBroadcast(null, "two", source, channel, middle, end);
        
        ImmutableList<ScheduleHierarchy> hiers = ImmutableList.<ScheduleHierarchy>of(
            ScheduleHierarchy.itemOnly(episode1), 
            ScheduleHierarchy.itemOnly(episode2)
        );
        
        Interval writtenInterval = new Interval(start, end);
        List<WriteResult<? extends Content>> results = store.writeSchedule(hiers, channel, writtenInterval);
        assertThat(results.size(), is(2));
        
        DateTime newEnd = new DateTime(2013,05,31,23,30,0,0,DateTimeZones.LONDON);
        
        //items 1 and 2 change 
        when(hasher.hash(argThat(isA(Content.class))))
            .thenReturn("differentOne")
            .thenReturn("oneDifferent")
            .thenReturn("differentTwo")
            .thenReturn("twoDifferent");
        
        episode1 = itemAndBroadcast(null, "one", source, channel, start, middle);
        ItemAndBroadcast episode3 = itemAndBroadcast(null, "three", source, channel, middle, newEnd);
        hiers = ImmutableList.<ScheduleHierarchy>of(
            ScheduleHierarchy.itemOnly(episode1), 
            ScheduleHierarchy.itemOnly(episode3)
        );
        
        results = store.writeSchedule(hiers, channel, new Interval(start, newEnd));
        assertThat(results.size(), is(2));
        assertTrue(Iterables.all(results, WriteResult.<Content>writtenFilter()));
        
        Interval requestedInterval = new Interval(
            new DateTime(2013,05,31,10,0,0,0,DateTimeZones.UTC), 
            new DateTime(2013,05,31,22,40,0,0,DateTimeZones.UTC) 
        );
        Schedule schedule = future(store.resolve(ImmutableList.of(channel), requestedInterval, source));
        ChannelSchedule channelSchedule = Iterables.getOnlyElement(schedule.channelSchedules());
        
        assertThat(channelSchedule.getChannel(), is(channel));
        assertThat(channelSchedule.getInterval(), is(requestedInterval));
        assertThat(channelSchedule.getEntries().size(), is(2));
        assertThat(channelSchedule.getEntries().get(0).getItem().getId().longValue(), is(1L));
        assertThat(channelSchedule.getEntries().get(1).getItem().getId().longValue(), is(3L));
        
        requestedInterval = new Interval(
            new DateTime(2013,05,31,23,0,0,0,DateTimeZones.UTC), 
            new DateTime(2013,06,01,22,40,0,0,DateTimeZones.UTC) 
        );
        schedule = future(store.resolve(ImmutableList.of(channel), requestedInterval, source));
        channelSchedule = Iterables.getOnlyElement(schedule.channelSchedules());
        
        assertThat(channelSchedule.getChannel(), is(channel));
        assertThat(channelSchedule.getInterval(), is(requestedInterval));
        assertThat(channelSchedule.getEntries().size(), is(0));
        
        Resolved<Content> resolved = future(contentStore.resolveIds(ImmutableList.of(Id.valueOf(2))));
        Item two = (Item) resolved.getResources().first().get();
        assertFalse(Iterables.getOnlyElement(Iterables.getOnlyElement(two.getVersions()).getBroadcasts()).isActivelyPublished());
    }

    private <T> T future(ListenableFuture<T> resolve) throws Exception {
        return Futures.get(resolve, 1, TimeUnit.SECONDS, Exception.class);
    }

    private ItemAndBroadcast itemAndBroadcast(Integer id, String alias, Publisher source, Channel channel, DateTime start, DateTime end) {
        Item item = item(id, source, alias);
        Version version = new Version();
        item.addVersion(version);
        Broadcast broadcast = broadcast(alias, channel, start, end);
        version.addBroadcast(broadcast);
        return new ItemAndBroadcast(item, broadcast);
    }

    private Broadcast broadcast(String broadacstId, Channel channel, DateTime start, DateTime end) {
        Broadcast b = new Broadcast(channel.getCanonicalUri(), start, end);
        b.withId(broadacstId);
        return b;
    }

    private Item item(Integer id, Publisher source, String alias) {
        Item item = new Item();
        if (id != null) {
            item.setId(id);
        }
        item.addAlias(new Alias("uri", alias));
        item.setPublisher(source);
        return item;
    }
    
}
