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

package com.github.nosan.embedded.cassandra.local.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.nosan.embedded.cassandra.Version;
import com.github.nosan.embedded.cassandra.util.ArchiveUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EmptyArtifact}.
 *
 * @author Dmytro Nosan
 */
class EmptyArtifactTests {

	private final EmptyArtifact artifact = new EmptyArtifact(new Version(3, 11, 3));

	@Test
	void shouldReturnAnEmptyArchive(@TempDir Path temporaryFolder) throws IOException {
		Path archive = this.artifact.get();
		Path temp = temporaryFolder.resolve("temp");
		Files.createDirectories(temp);
		ArchiveUtils.extract(archive, temp);
		assertThat(temp).isDirectory();
	}

}
