/*
 * Copyright 2018-2019 the original author or authors.
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

package com.github.nosan.embedded.cassandra.test.spring.data;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import com.github.nosan.embedded.cassandra.test.spring.EmbeddedCassandra;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample tests for {@link EmbeddedCassandra}.
 *
 * @author Dmytro Nosan
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@EmbeddedCassandra(scripts = "classpath:setup.cql", replace = EmbeddedCassandra.Replace.NONE)
public class EmbeddedCassandraCityRepositoryTests {

	@Autowired
	private CassandraTemplate cassandraTemplate;

	@Autowired
	private CityRepository cityRepository;

	@Test
	public void testRepository() {
		City city = new City();
		city.setId(Long.MAX_VALUE);
		city.setName("Lviv");
		city = this.cityRepository.save(city);
		assertThat(city.getId()).isNotNull();
		assertThat(this.cassandraTemplate.exists(city.getId(), City.class)).isTrue();
	}

}