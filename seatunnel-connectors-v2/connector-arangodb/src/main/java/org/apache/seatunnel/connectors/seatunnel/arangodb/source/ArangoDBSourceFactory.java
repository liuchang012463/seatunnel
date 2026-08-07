package org.apache.seatunnel.connectors.seatunnel.arangodb.source;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.options.ConnectorCommonOptions;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceSplit;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.CatalogTableUtil;
import org.apache.seatunnel.api.table.connector.TableSource;
import org.apache.seatunnel.api.table.factory.Factory;
import org.apache.seatunnel.api.table.factory.TableSourceFactory;
import org.apache.seatunnel.api.table.factory.TableSourceFactoryContext;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBOptions;

import com.google.auto.service.AutoService;

import java.io.Serializable;
import java.util.Collections;

@AutoService(Factory.class)
public class ArangoDBSourceFactory implements TableSourceFactory {

    @Override
    public String factoryIdentifier() {
        return ArangoDBOptions.PLUGIN_NAME;
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .required(ArangoDBOptions.HOSTS, ArangoDBOptions.DATABASE)
                .exclusive(ArangoDBOptions.QUERY, ArangoDBOptions.COLLECTION)
                .optional(
                        ArangoDBOptions.USERNAME,
                        ArangoDBOptions.PASSWORD,
                        ArangoDBOptions.QUERY_PARAMS,
                        ArangoDBOptions.USE_SSL,
                        ArangoDBOptions.VERIFY_HOST,
                        ArangoDBOptions.CONNECT_TIMEOUT_MS,
                        ArangoDBOptions.BATCH_SIZE,
                        ConnectorCommonOptions.SCHEMA)
                .build();
    }

    @Override
    public Class<? extends SeaTunnelSource> getSourceClass() {
        return ArangoDBSource.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, SplitT extends SourceSplit, StateT extends Serializable>
            TableSource<T, SplitT, StateT> createSource(TableSourceFactoryContext context) {
        ReadonlyConfig options = context.getOptions();
        CatalogTable catalogTable;
        if (options.getOptional(ConnectorCommonOptions.SCHEMA).isPresent()) {
            catalogTable = CatalogTableUtil.buildWithConfig(options);
        } else {
            catalogTable = CatalogTableUtil.buildSimpleTextTable();
        }
        ArangoDBSource source = new ArangoDBSource(catalogTable, ArangoDBConfig.fromSource(options));
        return () -> (SeaTunnelSource<T, SplitT, StateT>) source;
    }
}