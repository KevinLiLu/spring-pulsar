/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.pulsar.spring.cloud.stream.binder;

import org.springframework.cloud.stream.binder.AbstractPollableConsumerTestBinder;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.pulsar.spring.cloud.stream.binder.properties.PulsarConsumerProperties;
import org.springframework.pulsar.spring.cloud.stream.binder.properties.PulsarProducerProperties;

/**
 * Base class for {@link PulsarTestBinder}.
 *
 * @author Soby Chacko
 */
public abstract class AbstractPulsarTestBinder extends
		AbstractPollableConsumerTestBinder<PulsarMessageChannelBinder, ExtendedConsumerProperties<PulsarConsumerProperties>, ExtendedProducerProperties<PulsarProducerProperties>> {

	private ApplicationContext applicationContext;

	@Override
	public void cleanup() {
	}

	protected final void setApplicationContext(ApplicationContext context) {
		this.applicationContext = context;
	}

	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

}
