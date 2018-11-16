/*
 * Copyright 2018-2018 the original author or authors.
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

package com.github.nosan.embedded.cassandra.local;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.yaml.snakeyaml.Yaml;

import com.github.nosan.embedded.cassandra.Version;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PortReplacerCustomizer}.
 *
 * @author Dmytro Nosan
 */
public class PortReplacerCustomizerTests {


	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();


	@Test
	public void customize() throws Exception {
		Path directory = this.temporaryFolder.newFolder("conf").toPath();
		Version version = new Version(3, 11, 3);
		PortReplacerCustomizer customizer = new PortReplacerCustomizer(version);
		try (InputStream inputStream = ClassLoader.getSystemResourceAsStream("cassandra-all-ports.yaml")) {
			Files.copy(inputStream, directory.resolve("cassandra.yaml"));
		}

		customizer.customize(directory.getParent());

		assertThat(directory.resolve("cassandra.yaml")).exists();
		MapSettings settings;
		try (InputStream inputStream = Files.newInputStream(directory.resolve("cassandra.yaml"))) {
			settings = new MapSettings(new Yaml().loadAs(inputStream, Map.class), version);
		}
		assertThat(settings.getPort()).isNotEqualTo(0);
		assertThat(settings.getRpcPort()).isNotEqualTo(0);
		assertThat(settings.getStoragePort()).isNotEqualTo(0);
		assertThat(settings.getSslPort()).isNotEqualTo(0);
		assertThat(settings.getSslStoragePort()).isNotEqualTo(0);

	}

}