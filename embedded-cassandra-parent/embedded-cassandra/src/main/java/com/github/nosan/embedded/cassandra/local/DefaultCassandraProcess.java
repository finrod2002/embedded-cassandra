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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.github.nosan.embedded.cassandra.Cassandra;
import com.github.nosan.embedded.cassandra.Settings;
import com.github.nosan.embedded.cassandra.Version;
import com.github.nosan.embedded.cassandra.util.OS;
import com.github.nosan.embedded.cassandra.util.PortUtils;
import com.github.nosan.embedded.cassandra.util.ProcessUtils;
import com.github.nosan.embedded.cassandra.util.StringUtils;
import com.github.nosan.embedded.cassandra.util.SystemProperty;

/**
 * Default implementation of the {@link CassandraProcess}.
 *
 * @author Dmytro Nosan
 * @see DefaultCassandraProcessFactory
 * @since 1.0.9
 */
class DefaultCassandraProcess implements CassandraProcess {

	private static final Logger log = LoggerFactory.getLogger(Cassandra.class);

	@Nonnull
	private final Path directory;

	@Nonnull
	private final Duration startupTimeout;

	@Nonnull
	private final List<String> jvmOptions;

	@Nonnull
	private final Version version;

	@Nullable
	private final Path javaHome;

	@Nullable
	private Path pidFile;

	@Nullable
	private Process process;

	@Nullable
	private Settings settings;

	private long pid = -1;

	/**
	 * Creates a {@link DefaultCassandraProcess}.
	 *
	 * @param directory a configured directory
	 * @param startupTimeout a startup timeout
	 * @param jvmOptions additional {@code JVM} options
	 * @param version a version
	 * @param javaHome java home directory
	 */
	DefaultCassandraProcess(@Nonnull Path directory, @Nonnull Version version, @Nonnull Duration startupTimeout,
			@Nonnull List<String> jvmOptions, @Nullable Path javaHome) {
		this.directory = directory;
		this.startupTimeout = startupTimeout;
		this.version = version;
		this.javaHome = javaHome;
		this.jvmOptions = Collections.unmodifiableList(new ArrayList<>(jvmOptions));
	}

	@Override
	@Nonnull
	public Settings start() throws Exception {
		Path directory = this.directory;
		Settings settings = getSettings(directory, this.version);
		this.settings = settings;
		Path executable = OS.isWindows() ? directory.resolve("bin/cassandra.ps1") : directory.resolve("bin/cassandra");
		Path pidFile = directory.resolve(String.format("bin/%s.pid", UUID.randomUUID()));

		this.pidFile = pidFile;

		List<Object> arguments = new ArrayList<>();
		if (OS.isWindows()) {
			arguments.add("powershell");
			arguments.add("-ExecutionPolicy");
			arguments.add("Unrestricted");
		}
		arguments.add(executable.toAbsolutePath());
		arguments.add("-f");
		Version version = this.version;
		if (OS.isWindows()) {
			if (version.getMajor() > 2 || (version.getMajor() == 2 && version.getMinor() > 1)) {
				arguments.add("-a");
			}
		}
		arguments.add("-p");
		arguments.add(pidFile.toAbsolutePath());

		Map<String, String> environment = new LinkedHashMap<>();
		String javaHome = getJavaHome(this.javaHome);
		if (StringUtils.hasText(javaHome)) {
			environment.put("JAVA_HOME", javaHome);
		}
		List<String> jvmOptions = new ArrayList<>();
		int jmxPort = PortUtils.getPort();
		jvmOptions.add(String.format("-Dcassandra.jmx.local.port=%d", jmxPort));
		jvmOptions.addAll(this.jvmOptions);

		environment.put("JVM_EXTRA_OPTS", String.join(" ", jvmOptions));

		TransportUtils.verifyPorts(settings);

		OutputCapture outputCapture = new OutputCapture(20);
		Process process = new RunProcess(directory, environment, arguments)
				.run(outputCapture, log::info);

		this.process = process;
		this.pid = ProcessUtils.getPid(process);

		if (log.isDebugEnabled()) {
			log.debug("Cassandra Process ({}) has been started", getPidString(this.pid));
		}
		Duration timeout = this.startupTimeout;
		boolean result = WaitUtils.await(timeout, () -> {
			if (!process.isAlive()) {
				throwException("Cassandra Process is not alive. Please see logs for more details.", outputCapture);
			}
			return TransportUtils.isReady(settings);
		});
		if (!result) {
			throwException(String.format("(%s) milliseconds have past and Cassandra's transport is not ready." +
					" Please increase a startup timeout.", timeout.toMillis()), outputCapture);
		}
		return settings;
	}


	@Override
	public void stop() throws Exception {
		Process process = this.process;
		Path pidFile = this.pidFile;
		long pid = this.pid;
		Settings settings = this.settings;
		if (process != null && process.isAlive()) {
			try {
				if (log.isDebugEnabled()) {
					log.debug("Stops Cassandra process ({})", getPidString(pid));
				}
				try {
					stop(process, pidFile, pid);
				}
				catch (Exception ex) {
					forceStop(process, pidFile, pid);
					throw ex;
				}
				if (settings != null) {
					boolean result = WaitUtils.await(Duration.ofSeconds(5),
							() -> TransportUtils.isDisabled(settings) && !process.isAlive());
					if (!result) {
						forceStop(process, pidFile, pid);
					}
				}
				boolean waitFor = process.waitFor(3, TimeUnit.SECONDS);
				if (!waitFor) {
					throw new IOException(String.format("Casandra Process (%s) has not been stopped correctly",
							getPidString(pid)));
				}
			}
			finally {
				this.settings = null;
				this.pid = -1;
				this.pidFile = null;
				this.process = null;
			}
		}
	}


	private static Settings getSettings(Path directory, Version version) throws IOException {
		Path target = directory.resolve("conf/cassandra.yaml");
		try (InputStream is = Files.newInputStream(target)) {
			Yaml yaml = new Yaml();
			return new MapSettings(yaml.loadAs(is, Map.class), version);
		}
	}


	private static String getJavaHome(Path javaHome) {
		if (javaHome != null) {
			return String.valueOf(javaHome.toAbsolutePath());
		}
		return new SystemProperty("java.home").or("");
	}


	private static String getPidString(long pid) {
		return (pid > 0) ? String.valueOf(pid) : "???";
	}


	private static void stop(Process process, Path pidFile, long pid) throws IOException, InterruptedException {
		if (pidFile != null && Files.exists(pidFile)) {
			stop(pidFile, false);
		}
		else if (pid > 0) {
			stop(pid, false);
		}
		else {
			process.destroy();
		}
	}

	private static void forceStop(Process process, Path pidFile, long pid) {
		try {
			if (pidFile != null && Files.exists(pidFile)) {
				stop(pidFile, true);
			}
		}
		catch (Throwable ignore) {
		}
		try {
			if (pid > 0) {
				stop(pid, true);
			}
		}
		catch (Throwable ignore) {
		}
		try {
			process.destroyForcibly();
		}
		catch (Throwable ignore) {
		}
	}

	private static void stop(Path pidFile, boolean force) throws IOException, InterruptedException {
		if (OS.isWindows()) {
			List<Object> arguments = new ArrayList<>();
			arguments.add("powershell");
			arguments.add("-ExecutionPolicy");
			arguments.add("Unrestricted");
			arguments.add(pidFile.getParent().resolve("stop-server.ps1").toAbsolutePath());
			if (force) {
				arguments.add("-f");
			}
			arguments.add("-p");
			arguments.add(pidFile.toAbsolutePath());
			new RunProcess(arguments).runAndWait(log::info);
		}
		else {
			String signal = force ? "-9" : "-SIGINT";
			new RunProcess(Arrays.asList("bash", "-c",
					String.format("kill %s `cat %s`", signal, pidFile.toAbsolutePath()))).runAndWait(log::info);
		}
	}


	private static void stop(long pid, boolean force) throws IOException, InterruptedException {
		if (OS.isWindows()) {
			List<Object> arguments = new ArrayList<>();
			arguments.add("taskkill");
			if (force) {
				arguments.add("/F");
			}
			arguments.add("/T");
			arguments.add("/pid");
			arguments.add(pid);
			new RunProcess(arguments).runAndWait(log::info);
		}
		else {
			String signal = force ? "-9" : "-SIGINT";
			new RunProcess(Arrays.asList("kill", signal, pid)).runAndWait(log::info);
		}
	}


	private static void throwException(String message, OutputCapture outputCapture) throws IOException {
		StringBuilder builder = new StringBuilder(message);
		if (!outputCapture.isEmpty()) {
			Collection<String> lines = outputCapture.lines();
			builder.append(String.format(" Last (%s) lines:", lines.size()));
			for (String line : lines) {
				builder.append(String.format("%n%s", line));
			}
		}
		throw new IOException(builder.toString());
	}
}