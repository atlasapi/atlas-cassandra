package org.atlasapi.media;

import java.util.concurrent.Executors;

import org.atlasapi.media.content.CassandraContentStore;
import org.atlasapi.media.content.Content;
import org.atlasapi.media.content.ContentHasher;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.metabroadcast.common.ids.IdGenerator;
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


public class CassandraPersistenceModule extends AbstractIdleService implements PersisitenceModule {

    private final AstyanaxContext<Keyspace> context;
    private final IdGeneratorBuilder idGeneratorBuilder;
    
    public CassandraPersistenceModule(Iterable<String> seeds, int port, String cluster, String keyspace, int threadCount, int connectionTimeout, IdGeneratorBuilder idGeneratorBuilder) {
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
        this.idGeneratorBuilder = idGeneratorBuilder;
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
        ContentHasher hasher = contentHasher();
        IdGenerator idGenerator = idGeneratorBuilder.generator("content");
        return CassandraContentStore.builder(context, "content", hasher, idGenerator)
            .withReadConsistency(ConsistencyLevel.CL_QUORUM)
            .withWriteConsistency(ConsistencyLevel.CL_QUORUM)
            .build();
    }

    private ContentHasher contentHasher() {
        return new ContentHasher() {
            @Override
            public String hash(Content content) {
                return String.valueOf(content.hashCode());
            }
        };
    }

}
