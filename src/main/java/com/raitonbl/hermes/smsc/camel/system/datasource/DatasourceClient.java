package com.raitonbl.hermes.smsc.camel.system.datasource;

import com.raitonbl.hermes.smsc.camel.model.Entity;

import java.util.stream.Stream;

public interface DatasourceClient {
    <E extends Entity>Stream<E>findAll(RecordType type) throws Exception;
}
