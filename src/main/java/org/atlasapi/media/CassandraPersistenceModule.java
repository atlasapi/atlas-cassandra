package org.atlasapi.media;

import java.util.Random;
import java.util.concurrent.Executors;

import org.atlasapi.media.content.CassandraContentStore;
import org.atlasapi.media.content.ContentHasher;
import org.atlasapi.media.topic.CassandraTopicStore;
import org.atlasapi.media.topic.Topic;
import org.atlasapi.media.topic.TopicStore;

import com.google.common.base.Equivalence;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.metabroadcast.common.ids.IdGeneratorBuilder;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolType;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;


public class CassandraPersistenceModule extends AbstractIdleService implements PersistenceModule {

    private final AstyanaxContext<Keyspace> context;
    private final CassandraContentStore contentStore;
    private final CassandraTopicStore topicStore;
    
    public CassandraPersistenceModule(Iterable<String> seeds, int port, 
      String cluster, String keyspace, int threadCount, int connectionTimeout, 
      IdGeneratorBuilder idGeneratorBuilder, ContentHasher hasher) {
        context = new AstyanaxContext.Builder()
            .forCluster(cluster)
            .forKeyspace(keyspace)
            .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
                .setConnectionPoolType(ConnectionPoolType.ROUND_ROBIN)
                .setAsyncExecutor(Executors.newFixedThreadPool(
                    threadCount,
                    new ThreadFactoryBuilder().setDaemon(true)
                        .setNameFormat("astyanax-%d")
                        .build()
                ))
            )
            .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("altas")
                .setSeeds(Joiner.on(",").join(seeds))
                .setPort(port)
                .setConnectTimeout(connectionTimeout)
                .setMaxBlockedThreadsPerHost(threadCount)
                .setMaxConnsPerHost(threadCount)
                .setMaxConns(threadCount * 5)
            )
            .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
            .buildKeyspace(ThriftFamilyFactory.getInstance());

        this.contentStore = CassandraContentStore.builder(context, "content", 
            hasher, idGeneratorBuilder.generator("content"))
            .withReadConsistency(ConsistencyLevel.CL_ONE)
            .withWriteConsistency(ConsistencyLevel.CL_QUORUM)
            .build();
        this.topicStore = CassandraTopicStore.builder(context, "topic", 
            topicEquivalence(), idGeneratorBuilder.generator("content"))
            .withReadConsistency(ConsistencyLevel.CL_ONE)
            .withWriteConsistency(ConsistencyLevel.CL_QUORUM)
            .build();
    }

    @Override
    protected void startUp() throws Exception {
        context.start();
    }

    @Override
    protected void shutDown() throws Exception {
        context.shutdown();
    }
    
    @Override
    public CassandraContentStore contentStore() {
        return contentStore; 
    }

    @Override
    public TopicStore topicStore() {
        return topicStore;
    }

    private Equivalence<? super Topic> topicEquivalence() {
        return new Equivalence<Topic>(){

            private final Random random = new Random();

            @Override
            protected boolean doEquivalent(Topic a, Topic b) {
                return false;
            }

            @Override
            protected int doHash(Topic t) {
                return random.nextInt();
            }
        };
    }
}
