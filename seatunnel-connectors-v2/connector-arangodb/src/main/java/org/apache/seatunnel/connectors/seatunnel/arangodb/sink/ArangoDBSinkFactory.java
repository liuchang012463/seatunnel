package org.apache.seatunnel.connectors.seatunnel.arangodb.sink;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.table.connector.TableSink;
import org.apache.seatunnel.api.table.factory.Factory;
import org.apache.seatunnel.api.table.factory.TableSinkFactory;
import org.apache.seatunnel.api.table.factory.TableSinkFactoryContext;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBOptions;

import com.google.auto.service.AutoService;

@AutoService(Factory.class)
public class ArangoDBSinkFactory implements TableSinkFactory {

    @Override
    public String factoryIdentifier() {
        return ArangoDBOptions.PLUGIN_NAME;
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .required(ArangoDBOptions.HOSTS, ArangoDBOptions.DATABASE, ArangoDBOptions.COLLECTION)
                .optional(
                        ArangoDBOptions.USERNAME,
                        ArangoDBOptions.PASSWORD,
                        ArangoDBOptions.QUERY,
                        ArangoDBOptions.QUERY_PARAMS,
                        ArangoDBOptions.USE_SSL,
                        ArangoDBOptions.VERIFY_HOST,
                        ArangoDBOptions.CONNECT_TIMEOUT_MS,
                        ArangoDBOptions.MAX_BATCH_SIZE,
                        ArangoDBOptions.BATCH_DATA_VARIABLE)
                .build();
    }

    @Override
    public TableSink createSink(TableSinkFactoryContext context) {
        ReadonlyConfig options = context.getOptions();
        ArangoDBSink sink =
                new ArangoDBSink(context.getCatalogTable(), ArangoDBConfig.fromSink(options));
        return () -> sink;
    }
}