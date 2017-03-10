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

import org.infinispan.protostream.descriptors.Descriptor;
import org.infinispan.query.dsl.QueryBuilder;
import org.teiid.translator.MetadataProcessor;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.object.ObjectConnection;


/**
 * Each InfinispanCacheConnection implementation represents a connection to a local (i.e., embedded) cache
 * 
 * @author vhalbert
 */
public interface InfinispanHotRodConnection extends ObjectConnection {
		
	/**
	 * Call to return the QueryBuilder based on the table being queried
	 * @param tableName 
	 * @return QueryBuilder
	 * @throws TranslatorException
	 */
	public QueryBuilder getQueryBuilder(String tableName) throws TranslatorException;
	    
	/**
	 * Call to return the Descriptor from JDG cache based on the root class.
	 * 
	 * @return Descriptor
	 * @throws TranslatorException
	 */
	public Descriptor getDescriptor() throws TranslatorException;

	/**
	 * Call to return the Descriptor from JDG cache based on the specified class
	 * 
	 * @param clz
	 * @return Descriptor
	 * @throws TranslatorException
	 */
	public Descriptor getDescriptor(Class<?> clz) throws TranslatorException;
			
}

