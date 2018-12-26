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
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nosan.embedded.cassandra.Cassandra;
import com.github.nosan.embedded.cassandra.CassandraException;
import com.github.nosan.embedded.cassandra.Settings;
import com.github.nosan.embedded.cassandra.Version;
import com.github.nosan.embedded.cassandra.local.artifact.Artifact;
import com.github.nosan.embedded.cassandra.local.artifact.ArtifactFactory;
import com.github.nosan.embedded.cassandra.util.MDCUtils;
import com.github.nosan.embedded.cassandra.util.ThreadNameSupplier;
import com.github.nosan.embedded.cassandra.util.ThreadUtils;

/**
 * This {@link Cassandra} implementation just a wrapper on {@link CassandraProcess}.
 *
 * @author Dmytro Nosan
 * @see LocalCassandraFactory
 * @since 1.0.0
 */
class LocalCassandra implements Cassandra {

	private static final Logger log = LoggerFactory.getLogger(LocalCassandra.class);

	private static final AtomicLong instanceCounter = new AtomicLong();

	@Nonnull
	private final ThreadNameSupplier threadNameSupplier = new ThreadNameSupplier(String.format("cassandra-%d",
			instanceCounter.incrementAndGet()));

	@Nonnull
	private final Object lock = new Object();

	@Nonnull
	private final Version version;

	@Nonnull
	private final ArtifactFactory artifactFactory;

	@Nonnull
	private final DirectoryFactory directoryFactory;

	@Nonnull
	private final CassandraProcessFactory processFactory;

	@Nullable
	private volatile CassandraProcess process;

	@Nullable
	private volatile Directory directory;

	@Nullable
	private volatile Settings settings;

	@Nullable
	private volatile Thread ownerThread;

	private volatile boolean started;

	/**
	 * Creates a new {@link LocalCassandra}.
	 *
	 * @param version a version
	 * @param artifactFactory a factory to create {@link Artifact}
	 * @param workingDirectory a directory to keep data/logs/etc... (must be writable)
	 * @param startupTimeout a startup timeout
	 * @param configurationFile URL to {@code cassandra.yaml}
	 * @param logbackFile URL to {@code logback.xml}
	 * @param rackFile URL to {@code cassandra-rackdc.properties}
	 * @param topologyFile URL to {@code cassandra-topology.properties}
	 * @param commitLogArchivingFile URL to {@code commitlog_archiving.properties}
	 * @param jvmOptions additional {@code JVM} options
	 * @param javaHome java home directory
	 * @param jmxPort JMX port
	 * @param allowRoot allow running as a root
	 * @param registerShutdownHook whether shutdown hook should be registered or not
	 */
	LocalCassandra(@Nonnull Version version, @Nonnull ArtifactFactory artifactFactory,
			@Nonnull Path workingDirectory, @Nonnull Duration startupTimeout, @Nullable URL configurationFile,
			@Nullable URL logbackFile, @Nullable URL rackFile, @Nullable URL topologyFile,
			@Nullable URL commitLogArchivingFile, @Nonnull List<String> jvmOptions, @Nullable Path javaHome,
			int jmxPort, boolean allowRoot, boolean registerShutdownHook) {
		Objects.requireNonNull(artifactFactory, "Artifact Factory must not be null");
		Objects.requireNonNull(version, "Version must not be null");
		Objects.requireNonNull(startupTimeout, "Startup timeout must not be null");
		Objects.requireNonNull(jvmOptions, "JVM Options must not be null");
		Objects.requireNonNull(workingDirectory, "Working Directory must not be null");
		this.version = version;
		this.artifactFactory = artifactFactory;
		this.directoryFactory = new DefaultDirectoryFactory(version, workingDirectory,
				configurationFile, logbackFile, rackFile, topologyFile, commitLogArchivingFile);
		this.processFactory = new DefaultCassandraProcessFactory(startupTimeout, jvmOptions, version,
				javaHome, jmxPort, allowRoot);
		if (registerShutdownHook) {
			addShutdownHook();
		}
	}

	@Override
	public void start() throws CassandraException {
		synchronized (this.lock) {
			if (this.started) {
				return;
			}
			long start = System.currentTimeMillis();
			Version version = this.version;
			log.info("Starts Apache Cassandra ({}) ", version);
			this.started = true;

			AtomicReference<Throwable> throwable = new AtomicReference<>();
			Map<String, String> context = MDCUtils.getContext();
			Thread thread = new Thread(() -> {
				MDCUtils.setContext(context);
				try {
					startInternal();
				}
				catch (Throwable ex) {
					throwable.set(ex);
				}
			}, this.threadNameSupplier.get());
			this.ownerThread = thread;

			thread.start();
			join(thread);

			Throwable ex = throwable.get();
			if (ex != null) {
				try {
					stop();
				}
				catch (Throwable suppress) {
					ex.addSuppressed(suppress);
				}
				throw new CassandraException("Unable to start Cassandra", ex);
			}
			long end = System.currentTimeMillis();
			log.info("Apache Cassandra ({}) has been started ({} ms)", version, end - start);
		}
	}

	@Override
	public void stop() throws CassandraException {
		synchronized (this.lock) {
			if (!this.started) {
				return;
			}
			long start = System.currentTimeMillis();
			Version version = this.version;
			log.info("Stops Apache Cassandra ({}) ", version);

			Thread ownerThread = this.ownerThread;
			interrupt(ownerThread);
			join(ownerThread);
			this.ownerThread = null;

			AtomicReference<Throwable> throwable = new AtomicReference<>();
			Map<String, String> context = MDCUtils.getContext();
			Thread thread = new Thread(() -> {
				MDCUtils.setContext(context);
				try {
					stopInternal();
				}
				catch (Throwable ex) {
					throwable.set(ex);
				}
			}, this.threadNameSupplier.get());

			thread.start();
			join(thread);

			Throwable ex = throwable.get();
			if (ex != null) {
				throw new CassandraException("Unable to stop Cassandra", ex);
			}
			this.started = false;
			long end = System.currentTimeMillis();
			log.info("Apache Cassandra ({}) has been stopped ({} ms)", version, end - start);

		}
	}

	@Nonnull
	@Override
	public Settings getSettings() throws CassandraException {
		synchronized (this.lock) {
			Settings settings = this.settings;
			return Optional.ofNullable(settings)
					.orElseThrow(() -> new CassandraException(
							"Cassandra is not initialized. Please start it before calling this method."));
		}
	}

	@Override
	@Nonnull
	public String toString() {
		return String.format("%s [%s]", getClass().getSimpleName(), this.version);
	}

	private void startInternal() throws IOException {
		Version version = this.version;

		Artifact artifact = this.artifactFactory.create(version);
		Path archive = artifact.get();

		Directory directory = this.directoryFactory.create(archive);
		this.directory = directory;

		CassandraProcess process = this.processFactory.create(directory.initialize());
		this.process = process;
		this.settings = process.start();
	}

	private void stopInternal() throws IOException {
		CassandraProcess process = this.process;
		if (process != null) {
			process.stop();
		}

		this.process = null;
		this.settings = null;

		Directory directory = this.directory;
		if (directory != null) {
			try {
				directory.destroy();
			}
			catch (Throwable ex) {
				log.error(String.format("(%s) has not been deleted", directory), ex);
			}
		}
		this.directory = null;
	}

	private void addShutdownHook() {
		try {
			Runtime runtime = Runtime.getRuntime();
			runtime.addShutdownHook(new Thread(() -> {
				interrupt(this.ownerThread);
				stop();
			}, String.format("%s-hook", this.threadNameSupplier.get())));
		}
		catch (Throwable ex) {
			throw new CassandraException("Cassandra shutdown hook is not registered", ex);
		}
	}

	private static void join(Thread thread) {
		try {
			ThreadUtils.join(thread);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void interrupt(Thread thread) {
		try {
			ThreadUtils.interrupt(thread);
		}
		catch (SecurityException ex) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("%s is not able to interrupt a %s", Thread.currentThread(), thread), ex);
			}
		}
	}
}
