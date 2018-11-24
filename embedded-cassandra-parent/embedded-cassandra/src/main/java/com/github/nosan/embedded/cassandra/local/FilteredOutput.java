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

import java.util.function.Predicate;

import javax.annotation.Nonnull;

/**
 * {@link RunProcess.Output} to filter lines.
 *
 * @author Dmytro Nosan
 * @since 1.2.0
 */
class FilteredOutput implements RunProcess.Output {

	@Nonnull
	private final RunProcess.Output delegate;

	@Nonnull
	private final Predicate<String> filter;

	/**
	 * Creates a new {@link FilteredOutput}.
	 *
	 * @param delegate the destination output
	 * @param filter the include filter
	 */
	FilteredOutput(@Nonnull RunProcess.Output delegate, @Nonnull Predicate<String> filter) {
		this.delegate = delegate;
		this.filter = filter;
	}

	@Override
	public void accept(@Nonnull String line) {
		if (this.filter.test(line)) {
			this.delegate.accept(line);
		}
	}
}
