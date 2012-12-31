package org.atlasapi.media.content;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executors;

import org.atlasapi.media.common.Id;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.EntityType;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.ParentRef;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.util.Resolved;
import org.atlasapi.media.util.WriteResult;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.metabroadcast.common.collect.OptionalMap;
import com.metabroadcast.common.ids.IdGenerator;
import com.metabroadcast.common.time.Clock;
import com.metabroadcast.common.time.DateTimeZones;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolType;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;

@RunWith(MockitoJUnitRunner.class)
public class CassandraContentStoreIT {

    private static String seeds = "127.0.0.1";
    private static int clientThreads = 25;
    private static int connectionTimeout = 60000;
    private static int port = 9160;

    private static final AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
        .forCluster("Atlas")
        .forKeyspace("Atlas_Testing")
        .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
            .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
            .setConnectionPoolType(ConnectionPoolType.ROUND_ROBIN)
            .setAsyncExecutor(Executors.newFixedThreadPool(clientThreads,
                new ThreadFactoryBuilder().setDaemon(true)
                .setNameFormat("AstyanaxAsync-%d")
                .build()
            ))
        )
        .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("Altas")
            .setPort(port)
            .setMaxBlockedThreadsPerHost(clientThreads)
            .setMaxConnsPerHost(clientThreads)
            .setMaxConns(clientThreads * 5)
            .setConnectTimeout(connectionTimeout)
            .setSeeds(seeds)
        )
        .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
        .buildKeyspace(ThriftFamilyFactory.getInstance());
    
    private final ContentHasher hasher = mock(ContentHasher.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final Clock clock = mock(Clock.class);
    private final CassandraContentStore store = CassandraContentStore
        .builder(context, "Content", hasher, idGenerator)
        .withReadConsistency(ConsistencyLevel.CL_ONE)
        .withWriteConsistency(ConsistencyLevel.CL_ONE)
        .withClock(clock)
        .build();
    
    @BeforeClass
    public static void setup() throws ConnectionException {
        context.start();
        context.getEntity().createKeyspace(ImmutableMap.<String, Object> builder()
            .put("strategy_options", ImmutableMap.<String, Object> builder()
                .put("replication_factor", "1")
                .build())
            .put("strategy_class", "SimpleStrategy")
            .build()
            );
        context.getEntity().createColumnFamily(
            ColumnFamily.newColumnFamily("Content", LongSerializer.get(), 
                StringSerializer.get()),
            ImmutableMap.<String,Object>of()
        );
        context.getEntity().createColumnFamily(
            ColumnFamily.newColumnFamily("Content_aliases", StringSerializer.get(), 
                StringSerializer.get()),
                ImmutableMap.<String,Object>of()
        );
    }
    
    @AfterClass
    public static void tearDown() throws ConnectionException {
        context.getEntity().dropKeyspace();
    }
    
    @After
    public void clearCf() throws ConnectionException {
        context.getEntity().truncateColumnFamily("Content");
        context.getEntity().truncateColumnFamily("Content_aliases");
    }
    
    @Test
    public void testWriteAndReadTopLevelItem() {
        Content content = create(new Item());
        content.setTitle("title");
        
        DateTime now = new DateTime(DateTimeZones.UTC);
        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L);
        
        WriteResult<Content> writeResult = store.writeContent(content);
        assertTrue(writeResult.written());
        assertThat(writeResult.getResource().getId().longValue(), is(1234l));
        assertFalse(writeResult.getPrevious().isPresent());
        
        Resolved<Content> resolved = store.resolveIds(ImmutableList.of(content.getId()));
        Content item = Iterables.getOnlyElement(resolved.getResources());
        
        assertThat(item.getId(), is(writeResult.getResource().getId()));
        assertThat(item.getTitle(), is(content.getTitle()));
        assertThat(item.getFirstSeen(), is(now));
        assertThat(item.getLastUpdated(), is(now));
        assertThat(item.getThisOrChildLastUpdated(), is(now));
        
    }
    
    @Test
    public void testContentNotWrittenWhenHashNotChanged() {
        Content content = create(new Item());
        content.setTitle("title");
        
        DateTime now = new DateTime(DateTimeZones.UTC);
        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L);
        
        WriteResult<Content> writeResult = store.writeContent(content);
        assertTrue(writeResult.written());
        
        when(hasher.hash(argThat(isA(Content.class)))).thenReturn("same");

        writeResult = store.writeContent(writeResult.getResource());
        assertFalse(writeResult.written());
        
        verify(hasher, times(2)).hash(argThat(isA(Content.class)));
        verify(idGenerator, times(1)).generateRaw();
        verify(clock, times(1)).now();
        
        Resolved<Content> resolved = store.resolveIds(ImmutableList.of(content.getId()));
        Content item = Iterables.getOnlyElement(resolved.getResources());
        
        assertThat(item.getId(), is(content.getId()));
        assertThat(item.getTitle(), is(content.getTitle()));
        assertThat(item.getFirstSeen(), is(now));
        assertThat(item.getLastUpdated(), is(now));
        assertThat(item.getThisOrChildLastUpdated(), is(now));
        
    }

    @Test
    public void testContentWrittenWhenHashChanged() {
        Content content = create(new Item());
        content.setTitle("title");
        
        DateTime now = new DateTime(DateTimeZones.UTC);
        DateTime next = now.plusHours(1);
        when(clock.now())
            .thenReturn(now)
            .thenReturn(next);
        when(idGenerator.generateRaw()).thenReturn(1234L);
        
        WriteResult<Content> writeResult = store.writeContent(content);
        assertTrue(writeResult.written());
        
        Content resolved = resolve(content.getId().longValue());
        assertThat(resolved.getTitle(), is(content.getTitle()));
        
        when(hasher.hash(argThat(isA(Content.class))))
            .thenReturn("different")
            .thenReturn("differentAgain");

        writeResult = store.writeContent(writeResult.getResource());
        assertTrue(writeResult.written());
        
        verify(hasher, times(2)).hash(argThat(isA(Content.class)));
        verify(idGenerator, times(1)).generateRaw();
        verify(clock, times(2)).now();
        
        Content item = resolve(content.getId().longValue());
        
        assertThat(item.getId(), is(content.getId()));
        assertThat(item.getTitle(), is(content.getTitle()));
        assertThat(item.getFirstSeen(), is(now));
        assertThat(item.getLastUpdated(), is(next));
        assertThat(item.getThisOrChildLastUpdated(), is(next));
        
    }
    
    @Test
    public void testResolvesExistingContentByAlias() {

        Item bbcItem = new Item();
        bbcItem.setPublisher(Publisher.BBC);
        bbcItem.addAlias("shared Alias");
        bbcItem.setTitle("title");

        Item c4Item = new Item();
        c4Item.setPublisher(Publisher.C4);
        c4Item.addAlias("shared Alias");

        when(clock.now()).thenReturn(new DateTime(DateTimeZones.UTC));
        when(idGenerator.generateRaw())
            .thenReturn(1234L)
            .thenReturn(1235L);
        when(hasher.hash(argThat(isA(Content.class))))
            .thenReturn("different")
            .thenReturn("differentAgain");
        
        store.writeContent(bbcItem);
        store.writeContent(c4Item);
        
        Item resolvedItem = (Item) resolve(1234L);
        assertThat(resolvedItem.getTitle(), is(bbcItem.getTitle()));
        
        bbcItem.setTitle("newTitle");
        bbcItem.setId(null);
        WriteResult<Item> writtenContent = store.writeContent(bbcItem);
        assertThat(writtenContent.getPrevious().get().getTitle(), is("title"));
        
        resolvedItem = (Item) resolve(1234L);
        assertThat(resolvedItem.getTitle(), is(bbcItem.getTitle()));
        
        verify(clock, times(3)).now();
        verify(idGenerator, times(2)).generateRaw();
        verify(hasher, times(2)).hash(argThat(isA(Content.class)));
    }

    @Test(expected=IllegalStateException.class)
    public void testWritingItemWithMissingBrandFails() {
        Item item = create(new Item());
        item.setParentRef(new ParentRef(1235, EntityType.BRAND));
        
        store.writeContent(item);
        
        verify(idGenerator, never()).generateRaw();
        
    }

    @Test(expected=IllegalStateException.class)
    public void testWritingSeriesWithMissingBrandFails() {
        try {
            Series series = create(new Series());
            series.setParentRef(new ParentRef(1235, EntityType.BRAND));
            
            store.writeContent(series);
        } finally {
            verify(idGenerator, never()).generateRaw();
        }
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testWritingEpisodeWithoutBrandRefFails() {
        try {
                
            Episode episode = create(new Episode());
    
            store.writeContent(episode);
        
        }finally {
            verify(idGenerator, never()).generateRaw();
        }
    }
    
    @Test(expected=IllegalStateException.class)
    public void testWritingEpisodeWithoutBrandWrittenFails() {
        try {
                
            Series series = create(new Series());
            series.setParentRef(new ParentRef(666, EntityType.BRAND));
            
            Episode episode = create(new Episode());
    
            episode.setParentRef(new ParentRef(666, EntityType.BRAND));
            episode.setSeriesRef(new ParentRef(999, EntityType.SERIES));
            
            store.writeContent(episode);
        
        }finally {
            verify(idGenerator, never()).generateRaw();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testWritingEpisodeWithSeriesRefWithoutSeriesWrittenFails() {
        try {
            Brand brand = create(new Brand());

            Series series = create(new Series());
            series.setParentRef(new ParentRef(666, EntityType.BRAND));

            Episode episode = create(new Episode());

            episode.setParentRef(new ParentRef(666, EntityType.BRAND));
            episode.setSeriesRef(new ParentRef(999, EntityType.SERIES));

            when(clock.now()).thenReturn(new DateTime(DateTimeZones.UTC));
            when(idGenerator.generateRaw()).thenReturn(1234L);

            WriteResult<Brand> brandWriteResult = store.writeContent(brand);
            assertThat(brandWriteResult.getResource().getId().longValue(), is(1234L));
            
            store.writeContent(episode);

        } finally {
            //generate for brand but not episode
            verify(idGenerator, times(1)).generateRaw();
        }
    }
    
    @Test
    public void testWritingItemWritesRefIntoParent() {
        
        when(clock.now()).thenReturn(new DateTime(DateTimeZones.UTC));
        when(idGenerator.generateRaw())
            .thenReturn(1234L)
            .thenReturn(1235L);
        
        Brand brand = create(new Brand());
        
        store.writeContent(brand);
        
        Brand resolvedBrand = (Brand) resolve(1234L);
        assertThat(resolvedBrand.getChildRefs(), is(empty()));
        
        Item item = create(new Item());
        item.setContainer(resolvedBrand);
        
        store.writeContent(item);
        
        Item resolvedItem = (Item) resolve(1235L);
        
        assertThat(resolvedItem.getContainer().getId().longValue(), is(1234L));
        
    }
    
    @Test
    public void testWritingFullContentHierarchy() {
        
        DateTime now = new DateTime(DateTimeZones.UTC);
        
        Brand brand = create(new Brand());

        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L);
        WriteResult<Brand> brandWriteResult = store.writeContent(brand);
        
        Series series1 = create(new Series());
        series1.setParent(brandWriteResult.getResource());
        
        when(clock.now()).thenReturn(now.plusHours(1));
        when(idGenerator.generateRaw()).thenReturn(1235L);
        WriteResult<Series> series1WriteResult = store.writeContent(series1);

        Series series2 = create(new Series());
        series2.setParent(brandWriteResult.getResource());
        
        when(clock.now()).thenReturn(now.plusHours(1));
        when(idGenerator.generateRaw()).thenReturn(1236L);
        WriteResult<Series> series2WriteResult = store.writeContent(series2);
        
        Episode episode1 = create(new Episode());
        episode1.setContainer(brandWriteResult.getResource());
        episode1.setSeries(series1WriteResult.getResource());
        
        when(clock.now()).thenReturn(now.plusHours(2));
        when(idGenerator.generateRaw()).thenReturn(1237L);
        store.writeContent(episode1);
        
        Episode episode2 = create(new Episode());
        episode2.setContainer(brandWriteResult.getResource());
        episode2.setSeries(series2WriteResult.getResource());
        
        when(clock.now()).thenReturn(now.plusHours(2));
        when(idGenerator.generateRaw()).thenReturn(1238L);
        store.writeContent(episode2);

        Episode episode3 = create(new Episode());
        episode3.setContainer(brandWriteResult.getResource());
        episode3.setSeries(series1WriteResult.getResource());
        
        when(clock.now()).thenReturn(now.plusHours(3));
        when(idGenerator.generateRaw()).thenReturn(1239L);
        store.writeContent(episode3);
        
        Brand resolvedBrand = (Brand) resolve(1234L);
        assertThat(resolvedBrand.getFirstSeen(), is(now));
        assertThat(resolvedBrand.getLastUpdated(), is(now));
        assertThat(resolvedBrand.getThisOrChildLastUpdated(), is(now.plusHours(3)));
        assertThat(resolvedBrand.getSeriesRefs().size(), is(2));
        assertThat(resolvedBrand.getChildRefs().size(), is(3));

        Series resolvedSeries1 = (Series) resolve(1235L);
        assertThat(resolvedSeries1.getFirstSeen(), is(now.plusHours(1)));
        assertThat(resolvedSeries1.getLastUpdated(), is(now.plusHours(1)));
        assertThat(resolvedSeries1.getThisOrChildLastUpdated(), is(now.plusHours(3)));
        assertThat(resolvedSeries1.getParent().getId().longValue(), is(1234L));
        assertThat(resolvedSeries1.getChildRefs().size(), is(2));

        Series resolvedSeries2 = (Series) resolve(1236L);
        assertThat(resolvedSeries2.getFirstSeen(), is(now.plusHours(1)));
        assertThat(resolvedSeries2.getLastUpdated(), is(now.plusHours(1)));
        assertThat(resolvedSeries2.getThisOrChildLastUpdated(), is(now.plusHours(2)));
        assertThat(resolvedSeries2.getParent().getId().longValue(), is(1234L));
        assertThat(resolvedSeries2.getChildRefs().size(), is(1));

        Episode resolvedEpisode1 = (Episode) resolve(1237L);
        assertThat(resolvedEpisode1.getFirstSeen(), is(now.plusHours(2)));
        assertThat(resolvedEpisode1.getLastUpdated(), is(now.plusHours(2)));
        assertThat(resolvedEpisode1.getThisOrChildLastUpdated(), is(now.plusHours(2)));
        assertThat(resolvedEpisode1.getContainer().getId().longValue(), is(1234L));
        assertThat(resolvedEpisode1.getSeriesRef().getId().longValue(), is(1235L));
        assertThat(resolvedEpisode1.getContainerSummary().getTitle(), is("Brand"));

        Episode resolvedEpisode2 = (Episode) resolve(1238L);
        assertThat(resolvedEpisode2.getFirstSeen(), is(now.plusHours(2)));
        assertThat(resolvedEpisode2.getLastUpdated(), is(now.plusHours(2)));
        assertThat(resolvedEpisode2.getThisOrChildLastUpdated(), is(now.plusHours(2)));
        assertThat(resolvedEpisode2.getContainer().getId().longValue(), is(1234L));
        assertThat(resolvedEpisode2.getSeriesRef().getId().longValue(), is(1236L));
        assertThat(resolvedEpisode2.getContainerSummary().getTitle(), is("Brand"));
        
        Episode resolvedEpisode3 = (Episode) resolve(1239L);
        assertThat(resolvedEpisode3.getFirstSeen(), is(now.plusHours(3)));
        assertThat(resolvedEpisode3.getLastUpdated(), is(now.plusHours(3)));
        assertThat(resolvedEpisode3.getThisOrChildLastUpdated(), is(now.plusHours(3)));
        assertThat(resolvedEpisode3.getContainer().getId().longValue(), is(1234L));
        assertThat(resolvedEpisode3.getSeriesRef().getId().longValue(), is(1235L));
        assertThat(resolvedEpisode3.getContainerSummary().getTitle(), is("Brand"));
    }
    
    @Test
    public void testRewritingBrandReturnsChildRefsInWriteResultBrand() {
        
        DateTime now = new DateTime(DateTimeZones.UTC);
        
        Brand brand = create(new Brand());

        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L);
        WriteResult<Brand> brandWriteResult = store.writeContent(brand);
        
        Series series = create(new Series());
        series.setParent(brandWriteResult.getResource());
        
        when(clock.now()).thenReturn(now.plusHours(1));
        when(idGenerator.generateRaw()).thenReturn(1235L);
        WriteResult<Series> seriesWriteResult = store.writeContent(series);

        Episode episode = create(new Episode());
        episode.setContainer(brandWriteResult.getResource());
        episode.setSeries(seriesWriteResult.getResource());

        when(clock.now()).thenReturn(now.plusHours(2));
        when(idGenerator.generateRaw()).thenReturn(1237L);
        store.writeContent(episode);
        
        Brand writtenBrand = brandWriteResult.getResource();
        writtenBrand.setTitle("new title");
        when(hasher.hash(argThat(isA(Content.class))))
            .thenReturn("different")
            .thenReturn("differentAgain");
        
        brandWriteResult = store.writeContent(writtenBrand);
        writtenBrand = brandWriteResult.getResource();
        
        assertThat(writtenBrand.getChildRefs().size(), is(1));
        assertThat(writtenBrand.getSeriesRefs().size(), is(1));

        Series writtenSeries = seriesWriteResult.getResource();
        writtenSeries.setTitle("new title");
        when(hasher.hash(argThat(isA(Content.class))))
            .thenReturn("different")
            .thenReturn("differentAgain");
        
        seriesWriteResult = store.writeContent(writtenSeries);
        writtenSeries = seriesWriteResult.getResource();
        
        assertThat(writtenSeries.getParent().getId(), is(writtenBrand.getId()));
        assertThat(writtenSeries.getChildRefs().size(), is(1));
        
    }
    
    @Test
    public void testResolvingByAlias() {
        
        DateTime now = new DateTime(DateTimeZones.UTC);
        
        Brand brand = create(new Brand());
        brand.addAlias("bbcBrandAlias");

        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L);
        store.writeContent(brand);
        
        OptionalMap<String, Content> resolved = store.resolveAliases(
                ImmutableSet.of("bbcBrandAlias", "bbcSeriesAlias"), Publisher.BBC);
        
        assertThat(resolved.size(), is(1));
        assertThat(resolved.get("bbcBrandAlias").get().getId(), is(Id.valueOf(1234L)));
        
        Series series = create(new Series());
        series.addAlias("bbcSeriesAlias");
        
        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1235L);
        store.writeContent(series);
        
        resolved = store.resolveAliases(
            ImmutableSet.of("bbcBrandAlias", "bbcSeriesAlias"), Publisher.BBC);
        
        assertThat(resolved.size(), is(2));
        assertThat(resolved.get("bbcBrandAlias").get().getId(), is(Id.valueOf(1234L)));
        assertThat(resolved.get("bbcSeriesAlias").get().getId(), is(Id.valueOf(1235L)));
        
    }

    @Test
    public void testResolvingByAliasDoesntResolveContentFromAnotherSource() {
        
        DateTime now = new DateTime(DateTimeZones.UTC);
        
        Brand bbcBrand = create(new Brand());
        bbcBrand.addAlias("shared alias");
        
        Brand c4Brand = create(new Brand());
        c4Brand.setPublisher(Publisher.C4);
        c4Brand.addAlias("shared alias");
        
        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1234L);
        store.writeContent(bbcBrand);
        
        when(clock.now()).thenReturn(now);
        when(idGenerator.generateRaw()).thenReturn(1235L);
        store.writeContent(c4Brand);
        
        OptionalMap<String, Content> resolved = store.resolveAliases(
            ImmutableSet.of("shared alias"), Publisher.BBC);
        
        assertThat(resolved.size(), is(1));
        assertThat(resolved.get("shared alias").get().getId(), is(Id.valueOf(1234L)));
        
        resolved = store.resolveAliases(
            ImmutableSet.of("shared alias"), Publisher.C4);
        
        assertThat(resolved.size(), is(1));
        assertThat(resolved.get("shared alias").get().getId(), is(Id.valueOf(1235L)));
        
    }

    private <T extends Content> T create(T content) {
        content.setPublisher(Publisher.BBC);
        content.setTitle(content.getClass().getSimpleName());
        return content;
    }
    
    private Content resolve(Long id) {
        Resolved<Content> resolved = store.resolveIds(ImmutableList.of(Id.valueOf(id)));
        return Iterables.getOnlyElement(resolved.getResources());
    }

}
