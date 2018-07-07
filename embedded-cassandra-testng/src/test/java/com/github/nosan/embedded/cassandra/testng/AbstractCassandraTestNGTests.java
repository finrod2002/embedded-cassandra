/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nosan.embedded.cassandra.testng;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.github.nosan.embedded.cassandra.Config;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AbstractCassandraTestNG}.
 *
 * @author Dmytro Nosan
 */
public class AbstractCassandraTestNGTests extends AbstractCassandraTestNG {

	private Cluster cluster;

	@BeforeMethod
	public void setUp() throws Exception {
		Config config = getExecutableConfig().getConfig();
		this.cluster = Cluster.builder().withPort(config.getNativeTransportPort())
				.addContactPoint(config.getRpcAddress()).build();
		try (Session session = this.cluster.connect()) {
			session.execute("CREATE KEYSPACE  test  WITH REPLICATION = { 'class' : "
					+ "'SimpleStrategy', 'replication_factor' : 1 }; ");
			session.execute("CREATE TABLE  test.roles (   id text PRIMARY KEY );");
		}
	}

	@AfterMethod
	public void tearDown() throws Exception {
		if (this.cluster != null) {
			this.cluster.close();
		}
	}

	@Test
	public void select() {
		try (Session session = this.cluster.connect()) {
			assertThat(session.execute("SELECT * FROM  test.roles").wasApplied())
					.isTrue();
		}
	}

}
