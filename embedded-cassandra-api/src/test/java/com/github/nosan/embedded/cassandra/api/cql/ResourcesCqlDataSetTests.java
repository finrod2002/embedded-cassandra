/*
 * Copyright 2018-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nosan.embedded.cassandra.api.cql;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResourceCqlScript} and {@link CqlDataSet}.
 *
 * @author Dmytro Nosan
 */
class ResourcesCqlDataSetTests {

	private final CqlDataSet dataSet = CqlDataSet.ofClasspaths("schema.cql");

	@Test
	void testGetStatements() {
		List<String> statements = this.dataSet.getStatements();
		assertThat(statements).contains(
				"CREATE KEYSPACE test WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }");
		assertThat(statements).contains("CREATE TABLE test.roles ( id text PRIMARY KEY )");
	}

}
