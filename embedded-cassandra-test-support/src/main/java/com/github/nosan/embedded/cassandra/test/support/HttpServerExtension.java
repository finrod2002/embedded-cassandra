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

package com.github.nosan.embedded.cassandra.test.support;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * {@code JUnit5} extension to start/stop {@link HttpServer}.
 *
 * @author Dmytro Nosan
 * @since 1.4.2
 */
public final class HttpServerExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

	private static final Namespace NAMESPACE = Namespace.create(HttpServerExtension.class);

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		HttpServer httpServer = HttpServer.create();
		httpServer.setExecutor(Executors.newCachedThreadPool());
		httpServer.bind(new InetSocketAddress("localhost", 0), 0);
		httpServer.start();
		context.getStore(NAMESPACE).put(HttpServer.class, httpServer);
	}

	@Override
	public void afterEach(ExtensionContext context) {
		HttpServer httpServer = context.getStore(NAMESPACE).remove(HttpServer.class, HttpServer.class);
		if (httpServer != null) {
			httpServer.stop(0);
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return parameterContext.getParameter().getType().isAssignableFrom(HttpServer.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return extensionContext.getStore(NAMESPACE).get(HttpServer.class, HttpServer.class);
	}

}