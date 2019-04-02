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

package com.github.nosan.embedded.cassandra.local;

import com.github.nosan.embedded.cassandra.Version;

/**
 * Tests for {@link LocalCassandra}.
 *
 * @author Dmytro Nosan
 */
class LocalCassandra_V_3_11_X_Tests extends AbstractLocalCassandraTests {

	LocalCassandra_V_3_11_X_Tests() {
		super(new Version(3, 11, 4));
	}

}
