/*
 * Copyright 2018-2019 the original author or authors.
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

package com.github.nosan.embedded.cassandra.cql;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.github.nosan.embedded.cassandra.commons.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResourceCqlStatements}.
 *
 * @author Dmytro Nosan
 */
class ResourceCqlStatementsTests {

	private final CqlStatements statements = CqlStatements.forResources(new ClassPathResource("schema.cql"));

	@Test
	void testGetStatements() {
		List<String> statements = this.statements.stream().collect(Collectors.toList());
		assertThat(statements).contains(
				"CREATE KEYSPACE test WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }");
		assertThat(statements).contains("CREATE TABLE test.roles ( id text PRIMARY KEY )");
		assertThat(statements).contains(
				"CREATE TABLE test.users ( user_id text PRIMARY KEY, first_name text, last_name text, "
						+ "emails set<text> )");
	}

}
