package org.atlasapi.util;

import com.google.common.base.Function;
import com.netflix.astyanax.connectionpool.OperationResult;


public final class CassandraUtil {

    private CassandraUtil() {}
    
    private enum OperationResultToResultFunction implements Function<OperationResult<Object>, Object> {
        INSTANCE;

        @Override
        public Object apply(OperationResult<Object> opRes) {
            return opRes.getResult();
        }
        
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <R> Function<OperationResult<R>, R> toResult() {
        return (Function) OperationResultToResultFunction.INSTANCE;
    }
    
}
