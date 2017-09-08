/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */
package org.teiid.translator.infinispan.hotrod;

import java.util.List;
import java.util.Collection;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.BasicCache;
import org.teiid.infinispan.api.InfinispanConnection;
import org.teiid.language.ColumnReference;
import org.teiid.language.Command;
import org.teiid.language.NamedTable;
import org.teiid.language.QueryExpression;
import org.teiid.language.Select;
import org.teiid.language.visitor.CollectorVisitor;
import org.teiid.language.visitor.SQLStringVisitor;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;
import org.teiid.metadata.AbstractMetadataRecord;
import org.teiid.metadata.Column;
import org.teiid.metadata.RuntimeMetadata;
import org.teiid.metadata.Table;
import org.teiid.translator.DataNotAvailableException;
import org.teiid.translator.ExecutionContext;
import org.teiid.translator.ResultSetExecution;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.infinispan.hotrod.DocumentFilter.Action;

public class InfinispanQueryExecution implements ResultSetExecution {

	private Select command;
    private InfinispanConnection connection;
    private RuntimeMetadata metadata;
    private ExecutionContext executionContext;
    private InfinispanResponse results;
    private TeiidTableMarsheller marshaller;
    private boolean useAliasCache;

    public InfinispanQueryExecution(InfinispanExecutionFactory translator, QueryExpression command,
	                        ExecutionContext executionContext, RuntimeMetadata metadata, InfinispanConnection connection,
	                        boolean useAliasCache) throws TranslatorException {
	        this.command = (Select)command;
	        this.connection = connection;
	        this.metadata = metadata;
	        this.executionContext = executionContext;
	        this.useAliasCache = useAliasCache;
    }
    
    @Override
    public void execute() throws TranslatorException {
        try {
            if (useAliasCache) {
	            useModifiedGroups(this.connection, this.executionContext, this.metadata, this.command);
	        }
            final IckleConversionVisitor visitor = new IckleConversionVisitor(metadata, false);
            visitor.append(this.command);
            Table table = visitor.getParentTable();
            String queryStr = visitor.getQuery();
            LogManager.logDetail(LogConstants.CTX_CONNECTOR, "SourceQuery:", queryStr);

            DocumentFilter docFilter = null;
            if (queryStr.startsWith("FROM ") && ((Select)command).getWhere() != null) {
                SQLStringVisitor ssv = new SQLStringVisitor() {
                    @Override
                    public String getName(AbstractMetadataRecord object) {
                        return object.getName();
                    }
                };
                ssv.append(((Select)command).getWhere());
                docFilter = new ComplexDocumentFilter(visitor.getParentNamedTable(), visitor.getQueryNamedTable(),
                        this.metadata, ssv.toString(), Action.ADD);
            }

            this.marshaller = MarshallerBuilder.getMarshaller(table, this.metadata, docFilter);
            this.connection.registerMarshaller(this.marshaller);

            // if the message in defined in different cache than the default, switch it out now.
            RemoteCache<Object, Object> cache =  getCache(table, connection);
			results = new InfinispanResponse(cache, queryStr, this.executionContext.getBatchSize(),
					visitor.getRowLimit(), visitor.getRowOffset(), visitor.getProjectedDocumentAttributes(),
					visitor.getDocumentNode());
        } finally {
            this.connection.unRegisterMarshaller(this.marshaller);
        }
    }
    
	static void useModifiedGroups(InfinispanConnection connection, ExecutionContext context, RuntimeMetadata metadata,
			Command command) throws TranslatorException {
		BasicCache<String, String> aliasCache = InfinispanDirectQueryExecution.getAliasCache(connection);
		Collection<NamedTable> tables = CollectorVisitor.collectGroups(command);
		for(NamedTable namedTable : tables) {
//		CollectorVisitor.collectGroups(command).forEach(namedTable -> {
			try {
				Table table = InfinispanDirectQueryExecution.getAliasTable(context, metadata, aliasCache,
						namedTable.getMetadataObject());
				Collection<ColumnReference> columns = CollectorVisitor.collectElements(command);
				for(ColumnReference reference : columns) {
//				columns.forEach(reference -> {
					if (reference.getTable().getMetadataObject().equals(namedTable.getMetadataObject())) {
						Column column = table.getColumnByName(reference.getMetadataObject().getName());
						reference.getTable().setMetadataObject(table);
						reference.setMetadataObject(column);
					}
				};
				namedTable.setMetadataObject(table);
			} catch (TranslatorException e) {
				LogManager.logError(LogConstants.CTX_CONNECTOR, e, e.getMessage());
			}
		};
	}

    @Override
    public List<?> next() throws TranslatorException, DataNotAvailableException {
        try {
            this.connection.registerMarshaller(this.marshaller);
            return results.getNextRow();
        } catch(IOException e) {
        	throw new TranslatorException(e);
        }
        finally {
            this.connection.unRegisterMarshaller(this.marshaller);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void cancel() throws TranslatorException {
    }

    static RemoteCache<Object, Object> getCache(Table table, InfinispanConnection connection) throws TranslatorException {
        RemoteCache<Object, Object> cache = (RemoteCache<Object, Object>)connection.getCache();
        String cacheName = table.getProperty(ProtobufMetadataProcessor.CACHE, false);
        if (cacheName != null && !cacheName.equals(connection.getCache().getName())) {
            cache = ((RemoteCacheManager)connection.getCacheFactory()).getCache(cacheName);
        }
        return cache;
    }
}
