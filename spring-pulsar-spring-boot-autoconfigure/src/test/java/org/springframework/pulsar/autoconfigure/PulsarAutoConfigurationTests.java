/*
 * Copyright 2022-2023 the original author or authors.
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

package org.springframework.pulsar.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.api.interceptor.ProducerInterceptor;
import org.apache.pulsar.common.schema.KeyValueEncodingType;
import org.apache.pulsar.common.schema.SchemaType;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.pulsar.annotation.EnablePulsar;
import org.springframework.pulsar.annotation.PulsarBootstrapConfiguration;
import org.springframework.pulsar.annotation.PulsarListenerAnnotationBeanPostProcessor;
import org.springframework.pulsar.config.ConcurrentPulsarListenerContainerFactory;
import org.springframework.pulsar.config.PulsarClientFactoryBean;
import org.springframework.pulsar.config.PulsarListenerContainerFactory;
import org.springframework.pulsar.config.PulsarListenerEndpointRegistry;
import org.springframework.pulsar.core.CachingPulsarProducerFactory;
import org.springframework.pulsar.core.DefaultPulsarProducerFactory;
import org.springframework.pulsar.core.DefaultPulsarReaderFactory;
import org.springframework.pulsar.core.DefaultSchemaResolver;
import org.springframework.pulsar.core.DefaultTopicResolver;
import org.springframework.pulsar.core.PulsarAdministration;
import org.springframework.pulsar.core.PulsarConsumerFactory;
import org.springframework.pulsar.core.PulsarProducerFactory;
import org.springframework.pulsar.core.PulsarReaderFactory;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.pulsar.core.SchemaResolver;
import org.springframework.pulsar.core.SchemaResolver.SchemaResolverCustomizer;
import org.springframework.pulsar.core.TopicResolver;
import org.springframework.pulsar.function.PulsarFunctionAdministration;
import org.springframework.pulsar.listener.AckMode;
import org.springframework.pulsar.listener.PulsarContainerProperties;
import org.springframework.pulsar.observation.PulsarListenerObservationConvention;
import org.springframework.pulsar.observation.PulsarTemplateObservationConvention;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.observation.ObservationRegistry;

/**
 * Autoconfiguration tests for {@link PulsarAutoConfiguration}.
 *
 * @author Chris Bono
 * @author Alexander Preuß
 * @author Soby Chacko
 */
@SuppressWarnings("unchecked")
class PulsarAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(PulsarAutoConfiguration.class));

	@Test
	void autoConfigurationSkippedWhenPulsarTemplateNotOnClasspath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(PulsarTemplate.class))
				.run((context) -> assertThat(context).hasNotFailed().doesNotHaveBean(PulsarAutoConfiguration.class));
	}

	@Test
	void annotationDrivenConfigurationSkippedWhenEnablePulsarAnnotationNotOnClasspath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(EnablePulsar.class))
				.run((context) -> assertThat(context).hasNotFailed()
						.doesNotHaveBean(PulsarAnnotationDrivenConfiguration.class));
	}

	@Test
	void bootstrapConfigurationSkippedWhenCustomPulsarListenerAnnotationProcessorDefined() {
		this.contextRunner
				.withBean("org.springframework.pulsar.config.internalPulsarListenerAnnotationProcessor", String.class,
						() -> "someFauxBean")
				.run((context) -> assertThat(context).hasNotFailed()
						.doesNotHaveBean(PulsarBootstrapConfiguration.class));
	}

	@Test
	void defaultBeansAreAutoConfigured() {
		this.contextRunner.run((context) -> assertThat(context).hasNotFailed()
				.hasSingleBean(PulsarClientFactoryBean.class).hasSingleBean(PulsarProducerFactory.class)
				.hasSingleBean(PulsarTemplate.class).hasSingleBean(PulsarConsumerFactory.class)
				.hasSingleBean(ConcurrentPulsarListenerContainerFactory.class)
				.hasSingleBean(PulsarListenerAnnotationBeanPostProcessor.class)
				.hasSingleBean(PulsarListenerEndpointRegistry.class).hasSingleBean(PulsarAdministration.class)
				.hasSingleBean(DefaultSchemaResolver.class).hasSingleBean(DefaultTopicResolver.class));
	}

	@Test
	void customPulsarClientFactoryBeanIsRespected() {
		PulsarClientFactoryBean clientFactoryBean = new PulsarClientFactoryBean(
				new PulsarProperties().buildClientProperties());
		this.contextRunner
				.withBean("customPulsarClientFactoryBean", PulsarClientFactoryBean.class, () -> clientFactoryBean)
				.run((context) -> assertThat(context)
						.getBean("&customPulsarClientFactoryBean", PulsarClientFactoryBean.class)
						.isSameAs(clientFactoryBean));
	}

	@Test
	void customSchemaResolverIsRespected() {
		SchemaResolver customSchemaResolver = mock(SchemaResolver.class);
		this.contextRunner.withBean("customSchemaResolver", SchemaResolver.class, () -> customSchemaResolver)
				.run((context) -> assertThat(context).hasNotFailed().getBean(SchemaResolver.class)
						.isSameAs(customSchemaResolver));
	}

	@Test
	void defaultSchemaResolverCanBeCustomized() {
		record Foo() {
		}
		SchemaResolverCustomizer<DefaultSchemaResolver> customizer = (sr) -> sr.addCustomSchemaMapping(Foo.class,
				Schema.STRING);
		this.contextRunner.withBean("schemaResolverCustomizer", SchemaResolverCustomizer.class, () -> customizer)
				.run((context) -> assertThat(context).hasNotFailed().getBean(DefaultSchemaResolver.class)
						.extracting(DefaultSchemaResolver::getCustomSchemaMappings, InstanceOfAssertFactories.MAP)
						.containsEntry(Foo.class, Schema.STRING));
	}

	@Test
	void customTopicResolverIsRespected() {
		TopicResolver customTopicResolver = mock(TopicResolver.class);
		this.contextRunner.withBean("customTopicResolver", TopicResolver.class, () -> customTopicResolver)
				.run((context) -> assertThat(context).hasNotFailed().getBean(TopicResolver.class)
						.isSameAs(customTopicResolver));
	}

	@Test
	void customPulsarProducerFactoryIsRespected() {
		PulsarProducerFactory<String> producerFactory = mock(PulsarProducerFactory.class);
		this.contextRunner.withBean("customPulsarProducerFactory", PulsarProducerFactory.class, () -> producerFactory)
				.run((context) -> assertThat(context).hasNotFailed().getBean(PulsarProducerFactory.class)
						.isSameAs(producerFactory));
	}

	@Test
	void customPulsarTemplateIsRespected() {
		PulsarTemplate<String> template = mock(PulsarTemplate.class);
		this.contextRunner.withBean("customPulsarTemplate", PulsarTemplate.class, () -> template)
				.run((context) -> assertThat(context).hasNotFailed().getBean(PulsarTemplate.class).isSameAs(template));
	}

	@Test
	void beansAreInjectedInPulsarTemplate() {
		PulsarProducerFactory<?> producerFactory = mock(PulsarProducerFactory.class);
		SchemaResolver schemaResolver = mock(SchemaResolver.class);
		TopicResolver topicResolver = mock(TopicResolver.class);
		this.contextRunner.withBean("customPulsarProducerFactory", PulsarProducerFactory.class, () -> producerFactory)
				.withBean("schemaResolver", SchemaResolver.class, () -> schemaResolver)
				.withBean("topicResolver", TopicResolver.class, () -> topicResolver)
				.run((context -> assertThat(context).hasNotFailed().getBean(PulsarTemplate.class)
						.hasFieldOrPropertyWithValue("producerFactory", producerFactory)
						.hasFieldOrPropertyWithValue("schemaResolver", schemaResolver)
						.hasFieldOrPropertyWithValue("topicResolver", topicResolver)));
	}

	@Test
	void customPulsarConsumerFactoryIsRespected() {
		PulsarConsumerFactory<String> consumerFactory = mock(PulsarConsumerFactory.class);
		this.contextRunner.withBean("customPulsarConsumerFactory", PulsarConsumerFactory.class, () -> consumerFactory)
				.run((context) -> assertThat(context).hasNotFailed().getBean(PulsarConsumerFactory.class)
						.isSameAs(consumerFactory));
	}

	@Test
	void pulsarConsumerFactoryWithEnumPropertyValue() {
		this.contextRunner.withPropertyValues("spring.pulsar.consumer.subscription-initial-position=earliest")
				.run((context -> assertThat(context).hasNotFailed().getBean(PulsarConsumerFactory.class)
						.extracting("consumerConfig").hasFieldOrPropertyWithValue("subscriptionInitialPosition",
								SubscriptionInitialPosition.Earliest)));
	}

	@Test
	void customPulsarListenerContainerFactoryIsRespected() {
		PulsarListenerContainerFactory listenerContainerFactory = mock(PulsarListenerContainerFactory.class);
		this.contextRunner
				.withBean("pulsarListenerContainerFactory", PulsarListenerContainerFactory.class,
						() -> listenerContainerFactory)
				.run((context) -> assertThat(context).hasNotFailed().getBean(PulsarListenerContainerFactory.class)
						.isSameAs(listenerContainerFactory));
	}

	@Test
	void beansAreInjectedInPulsarListenerContainerFactory() {
		PulsarConsumerFactory<?> consumerFactory = mock(PulsarConsumerFactory.class);
		SchemaResolver schemaResolver = mock(SchemaResolver.class);
		TopicResolver topicResolver = mock(TopicResolver.class);
		this.contextRunner.withBean("pulsarConsumerFactory", PulsarConsumerFactory.class, () -> consumerFactory)
				.withBean("schemaResolver", SchemaResolver.class, () -> schemaResolver)
				.withBean("topicResolver", TopicResolver.class, () -> topicResolver)
				.run((context -> assertThat(context).hasNotFailed()
						.getBean(ConcurrentPulsarListenerContainerFactory.class)
						.hasFieldOrPropertyWithValue("consumerFactory", consumerFactory)
						.extracting(ConcurrentPulsarListenerContainerFactory<Object>::getContainerProperties)
						.hasFieldOrPropertyWithValue("schemaResolver", schemaResolver)
						.hasFieldOrPropertyWithValue("topicResolver", topicResolver)));
	}

	@Test
	void customPulsarListenerAnnotationBeanPostProcessorIsRespected() {
		PulsarListenerAnnotationBeanPostProcessor<String> listenerAnnotationBeanPostProcessor = mock(
				PulsarListenerAnnotationBeanPostProcessor.class);
		this.contextRunner
				.withBean("org.springframework.pulsar.config.internalPulsarListenerAnnotationProcessor",
						PulsarListenerAnnotationBeanPostProcessor.class, () -> listenerAnnotationBeanPostProcessor)
				.run((context) -> assertThat(context).hasNotFailed()
						.getBean(PulsarListenerAnnotationBeanPostProcessor.class)
						.isSameAs(listenerAnnotationBeanPostProcessor));
	}

	@Test
	void customPulsarAdministrationIsRespected() {
		PulsarAdministration pulsarAdministration = mock(PulsarAdministration.class);
		this.contextRunner
				.withBean("customPulsarAdministration", PulsarAdministration.class, () -> pulsarAdministration)
				.run((context) -> assertThat(context).hasNotFailed().getBean(PulsarAdministration.class)
						.isSameAs(pulsarAdministration));
	}

	@Test
	void customProducerInterceptorIsUsedInPulsarTemplate() {
		ProducerInterceptor interceptor = mock(ProducerInterceptor.class);
		this.contextRunner.withBean("customProducerInterceptor", ProducerInterceptor.class, () -> interceptor)
				.run((context -> assertThat(context).hasNotFailed().getBean(PulsarTemplate.class)
						.extracting("interceptors")
						.asInstanceOf(InstanceOfAssertFactories.list(ProducerInterceptor.class))
						.contains(interceptor)));
	}

	@Test
	void customProducerInterceptorsOrderedProperly() {
		this.contextRunner.withUserConfiguration(InterceptorTestConfiguration.class)
				.run((context -> assertThat(context).hasNotFailed().getBean(PulsarTemplate.class)
						.extracting("interceptors")
						.asInstanceOf(InstanceOfAssertFactories.list(ProducerInterceptor.class))
						.containsExactly(InterceptorTestConfiguration.interceptorBar,
								InterceptorTestConfiguration.interceptorFoo)));
	}

	@Test
	void listenerPropertiesAreHonored() {
		contextRunner
				.withPropertyValues("spring.pulsar.listener.ack-mode=manual", "spring.pulsar.listener.schema-type=avro",
						"spring.pulsar.listener.max-num-messages=10", "spring.pulsar.listener.max-num-bytes=101B",
						"spring.pulsar.listener.batch-timeout=50ms", "spring.pulsar.consumer.subscription-type=shared")
				.run((context -> {
					AbstractObjectAssert<?, PulsarContainerProperties> properties = assertThat(context).hasNotFailed()
							.getBean(ConcurrentPulsarListenerContainerFactory.class)
							.extracting(ConcurrentPulsarListenerContainerFactory<Object>::getContainerProperties);
					properties.extracting(PulsarContainerProperties::getAckMode).isEqualTo(AckMode.MANUAL);
					properties.extracting(PulsarContainerProperties::getSchemaType).isEqualTo(SchemaType.AVRO);
					properties.extracting(PulsarContainerProperties::getMaxNumMessages).isEqualTo(10);
					properties.extracting(PulsarContainerProperties::getMaxNumBytes).isEqualTo(101);
					properties.extracting(PulsarContainerProperties::getBatchTimeoutMillis).isEqualTo(50);
					properties.extracting(PulsarContainerProperties::getSubscriptionType)
							.isEqualTo(SubscriptionType.Shared);
				}));
	}

	@Nested
	class DefaultsTypeMappingsTests {

		@Test
		void topicMappingsAreAddedToTopicResolver() {
			contextRunner
					.withPropertyValues(
							"spring.pulsar.defaults.type-mappings[0].message-type=%s".formatted(Foo.class.getName()),
							"spring.pulsar.defaults.type-mappings[0].topic-name=foo-topic",
							"spring.pulsar.defaults.type-mappings[1].message-type=%s".formatted(String.class.getName()),
							"spring.pulsar.defaults.type-mappings[1].topic-name=string-topic")
					.run((context -> assertThat(context).hasNotFailed().getBean(TopicResolver.class)
							.asInstanceOf(InstanceOfAssertFactories.type(DefaultTopicResolver.class))
							.extracting(DefaultTopicResolver::getCustomTopicMappings, InstanceOfAssertFactories.MAP)
							.containsOnly(entry(Foo.class, "foo-topic"), entry(String.class, "string-topic"))));
		}

		@Test
		void schemaMappingForPrimitiveIsAddedToSchemaResolver() {
			contextRunner
					.withPropertyValues(
							"spring.pulsar.defaults.type-mappings[0].message-type=%s".formatted(Foo.class.getName()),
							"spring.pulsar.defaults.type-mappings[0].schema-info.schema-type=STRING")
					.run((context -> assertThat(context).hasNotFailed().getBean(SchemaResolver.class)
							.asInstanceOf(InstanceOfAssertFactories.type(DefaultSchemaResolver.class))
							.extracting(DefaultSchemaResolver::getCustomSchemaMappings, InstanceOfAssertFactories.MAP)
							.containsOnly(entry(Foo.class, Schema.STRING))));
		}

		@Test
		void schemaMappingForStructIsAddedToSchemaResolver() {
			contextRunner
					.withPropertyValues(
							"spring.pulsar.defaults.type-mappings[0].message-type=%s".formatted(Foo.class.getName()),
							"spring.pulsar.defaults.type-mappings[0].schema-info.schema-type=JSON")
					.run((context -> assertThat(context).hasNotFailed().getBean(SchemaResolver.class)
							.asInstanceOf(InstanceOfAssertFactories.type(DefaultSchemaResolver.class))
							.extracting(DefaultSchemaResolver::getCustomSchemaMappings,
									InstanceOfAssertFactories.map(Class.class, Schema.class))
							.hasEntrySatisfying(Foo.class,
									(schema) -> assertSchemaEquals(schema, Schema.JSON(Foo.class)))));
		}

		@Test
		void schemaMappingForKeyValueIsAddedToSchemaResolver() {
			contextRunner
					.withPropertyValues(
							"spring.pulsar.defaults.type-mappings[0].message-type=%s".formatted(Foo.class.getName()),
							"spring.pulsar.defaults.type-mappings[0].schema-info.schema-type=%s"
									.formatted(SchemaType.KEY_VALUE.name()),
							"spring.pulsar.defaults.type-mappings[0].schema-info.message-key-type=%s"
									.formatted(String.class.getName()))
					.run((context -> assertThat(context).hasNotFailed().getBean(SchemaResolver.class)
							.asInstanceOf(InstanceOfAssertFactories.type(DefaultSchemaResolver.class))
							.extracting(DefaultSchemaResolver::getCustomSchemaMappings,
									InstanceOfAssertFactories.map(Class.class, Schema.class))
							.hasEntrySatisfying(Foo.class, (schema) -> assertSchemaEquals(schema, Schema
									.KeyValue(Schema.STRING, Schema.JSON(Foo.class), KeyValueEncodingType.INLINE)))));
		}

		private void assertSchemaEquals(Schema<?> left, Schema<?> right) {
			assertThat(left.getSchemaInfo()).isEqualTo(right.getSchemaInfo());
		}

		record Foo() {
		}

	}

	@Nested
	class ClientAutoConfigurationTests {

		@Test
		void authParamMapConvertedToEncodedParamString() {
			contextRunner.withPropertyValues(
					"spring.pulsar.client.auth-plugin-class-name=org.apache.pulsar.client.impl.auth.AuthenticationBasic",
					"spring.pulsar.client.authentication.userId=username",
					"spring.pulsar.client.authentication.password=topsecret")
					.run((context -> assertThat(context).hasNotFailed().getBean(PulsarClientFactoryBean.class)
							.extracting("config", InstanceOfAssertFactories.map(String.class, Object.class))
							.doesNotContainKey("authParamMap").doesNotContainKey("userId").doesNotContainKey("password")
							.containsEntry("authParams", "{\"password\":\"topsecret\",\"userId\":\"username\"}")));
		}

	}

	@Nested
	class FunctionAutoConfigurationTests {

		@Test
		void functionSupportEnabledByDefault() {
			// NOTE: hasNoNullFieldsOrProperties() ensures object providers set
			contextRunner.run(context -> assertThat(context).hasNotFailed().getBean(PulsarFunctionAdministration.class)
					.hasFieldOrPropertyWithValue("failFast", Boolean.TRUE)
					.hasFieldOrPropertyWithValue("propagateFailures", Boolean.TRUE)
					.hasFieldOrPropertyWithValue("propagateStopFailures", Boolean.FALSE).hasNoNullFieldsOrProperties()
					.extracting("pulsarAdministration").isSameAs(context.getBean(PulsarAdministration.class)));
		}

		@Test
		void functionSupportCanBeConfigured() {
			contextRunner
					.withPropertyValues("spring.pulsar.function.fail-fast=false",
							"spring.pulsar.function.propagate-failures=false",
							"spring.pulsar.function.propagate-stop-failures=true")
					.run(context -> assertThat(context).hasNotFailed().getBean(PulsarFunctionAdministration.class)
							.hasFieldOrPropertyWithValue("failFast", Boolean.FALSE)
							.hasFieldOrPropertyWithValue("propagateFailures", Boolean.FALSE)
							.hasFieldOrPropertyWithValue("propagateStopFailures", Boolean.TRUE));
		}

		@Test
		void functionSupportCanBeDisabled() {
			contextRunner.withPropertyValues("spring.pulsar.function.enabled=false").run(
					context -> assertThat(context).hasNotFailed().doesNotHaveBean(PulsarFunctionAdministration.class));
		}

		@Test
		void customFunctionAdminIsRespected() {
			PulsarFunctionAdministration customFunctionAdmin = mock(PulsarFunctionAdministration.class);
			contextRunner.withBean(PulsarFunctionAdministration.class, () -> customFunctionAdmin)
					.run(context -> assertThat(context).hasNotFailed().getBean(PulsarFunctionAdministration.class)
							.isSameAs(customFunctionAdmin));
		}

	}

	@Nested
	class ObservationAutoConfigurationTests {

		@Test
		void templateObservationsEnabledByDefault() {
			ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
			contextRunner.withBean("observationRegistry", ObservationRegistry.class, () -> observationRegistry)
					.run((context -> assertThat(context).hasNotFailed().getBean(PulsarTemplate.class)
							.extracting("observationRegistry").isSameAs(observationRegistry)));
		}

		@Test
		void templateObservationsCanBeDisabled() {
			ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
			contextRunner.withPropertyValues("spring.pulsar.template.observations-enabled=false")
					.withBean("observationRegistry", ObservationRegistry.class, () -> observationRegistry)
					.run((context -> assertThat(context).hasNotFailed().getBean(PulsarTemplate.class)
							.extracting("observationRegistry").isNull()));
		}

		@Test
		void templateObservationsWithCustomConvention() {
			ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
			PulsarTemplateObservationConvention customConvention = mock(PulsarTemplateObservationConvention.class);
			contextRunner.withBean("observationRegistry", ObservationRegistry.class, () -> observationRegistry)
					.withBean("customConvention", PulsarTemplateObservationConvention.class, () -> customConvention)
					.run((context -> assertThat(context).hasNotFailed().getBean(PulsarTemplate.class)
							.extracting("observationConvention").isSameAs(customConvention)));
		}

		@Test
		void listenerObservationsEnabledByDefault() {
			ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
			contextRunner.withBean("observationRegistry", ObservationRegistry.class, () -> observationRegistry)
					.run((context -> assertThat(context).hasNotFailed()
							.getBean(ConcurrentPulsarListenerContainerFactory.class).extracting("observationRegistry")
							.isSameAs(observationRegistry)));
		}

		@Test
		void listenerObservationsCanBeDisabled() {
			ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
			contextRunner.withPropertyValues("spring.pulsar.listener.observations-enabled=false")
					.withBean("observationRegistry", ObservationRegistry.class, () -> observationRegistry)
					.run((context -> assertThat(context).hasNotFailed()
							.getBean(ConcurrentPulsarListenerContainerFactory.class).extracting("observationRegistry")
							.isNull()));
		}

		@Test
		void listenerObservationsWithCustomConvention() {
			ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
			PulsarListenerObservationConvention customConvention = mock(PulsarListenerObservationConvention.class);
			contextRunner.withBean("observationRegistry", ObservationRegistry.class, () -> observationRegistry)
					.withBean("customConvention", PulsarListenerObservationConvention.class, () -> customConvention)
					.run((context -> assertThat(context).hasNotFailed()
							.getBean(ConcurrentPulsarListenerContainerFactory.class)
							.extracting(ConcurrentPulsarListenerContainerFactory<Object>::getContainerProperties)
							.extracting(PulsarContainerProperties::getObservationConvention)
							.isSameAs(customConvention)));
		}

	}

	@Nested
	class ProducerFactoryAutoConfigurationTests {

		@Test
		void cachingProducerFactoryEnabledByDefault() {
			contextRunner.run((context) -> assertHasProducerFactoryOfType(CachingPulsarProducerFactory.class, context));
		}

		@Test
		void nonCachingProducerFactoryCanBeEnabled() {
			contextRunner.withPropertyValues("spring.pulsar.producer.cache.enabled=false")
					.run((context -> assertHasProducerFactoryOfType(DefaultPulsarProducerFactory.class, context)));
		}

		@Test
		void cachingProducerFactoryCanBeEnabled() {
			contextRunner.withPropertyValues("spring.pulsar.producer.cache.enabled=true")
					.run((context -> assertHasProducerFactoryOfType(CachingPulsarProducerFactory.class, context)));
		}

		@Test
		void cachingEnabledAndCaffeineNotOnClasspath() {
			contextRunner.withClassLoader(new FilteredClassLoader(Caffeine.class))
					.withPropertyValues("spring.pulsar.producer.cache.enabled=true")
					.run((context -> assertHasProducerFactoryOfType(CachingPulsarProducerFactory.class, context)));
		}

		@Test
		void cachingProducerFactoryCanBeConfigured() {
			contextRunner
					.withPropertyValues("spring.pulsar.producer.cache.expire-after-access=100s",
							"spring.pulsar.producer.cache.maximum-size=5150",
							"spring.pulsar.producer.cache.initial-capacity=200")
					.run((context -> assertThat(context).hasNotFailed().getBean(PulsarProducerFactory.class)
							.extracting("producerCache.cache.cache").hasFieldOrPropertyWithValue("maximum", 5150L)
							.hasFieldOrPropertyWithValue("expiresAfterAccessNanos", TimeUnit.SECONDS.toNanos(100))));
		}

		@Test
		void beansAreInjectedInNonCachingProducerFactory() {
			contextRunner.withPropertyValues("spring.pulsar.producer.cache.enabled=false")
					.run((context -> assertThat(context).hasNotFailed().getBean(DefaultPulsarProducerFactory.class)
							.hasFieldOrPropertyWithValue("pulsarClient", context.getBean(PulsarClient.class))
							.hasFieldOrPropertyWithValue("topicResolver", context.getBean(TopicResolver.class))));
		}

		@Test
		void beansAreInjectedInCachingProducerFactory() {
			contextRunner.withPropertyValues("spring.pulsar.producer.cache.enabled=true")
					.run((context -> assertThat(context).hasNotFailed().getBean(CachingPulsarProducerFactory.class)
							.hasFieldOrPropertyWithValue("pulsarClient", context.getBean(PulsarClient.class))
							.hasFieldOrPropertyWithValue("topicResolver", context.getBean(TopicResolver.class))));
		}

		private void assertHasProducerFactoryOfType(Class<?> producerFactoryType,
				AssertableApplicationContext context) {
			assertThat(context).hasNotFailed().hasSingleBean(PulsarProducerFactory.class)
					.getBean(PulsarProducerFactory.class).isExactlyInstanceOf(producerFactoryType);
		}

	}

	@Nested
	class ReaderFactoryAutoConfigurationTests {

		@Test
		void readerFactoryIsAutoConfiguredByDefault() {
			contextRunner.run((context) -> assertThat(context).hasNotFailed().hasSingleBean(PulsarReaderFactory.class)
					.getBean(PulsarReaderFactory.class).isExactlyInstanceOf(DefaultPulsarReaderFactory.class));
		}

		@Test
		void readerFactoryCanBeConfigured() {
			contextRunner.withPropertyValues("spring.pulsar.reader.topic-names=foo",
					"spring.pulsar.reader.receiver-queue-size=200", "spring.pulsar.reader.reader-name=test-reader",
					"spring.pulsar.reader.subscription-name=test-subscription",
					"spring.pulsar.reader.subscription-role-prefix=test-prefix",
					"spring.pulsar.reader.read-compacted=true", "spring.pulsar.reader.reset-include-head=true")
					.run((context -> assertThat(context).hasNotFailed().getBean(PulsarReaderFactory.class)
							.extracting("readerConfig").hasFieldOrPropertyWithValue("topicNames", List.of("foo"))
							.hasFieldOrPropertyWithValue("receiverQueueSize", 200)
							.hasFieldOrPropertyWithValue("readerName", "test-reader")
							.hasFieldOrPropertyWithValue("subscriptionName", "test-subscription")
							.hasFieldOrPropertyWithValue("subscriptionRolePrefix", "test-prefix")
							.hasFieldOrPropertyWithValue("readCompacted", true)
							.hasFieldOrPropertyWithValue("resetIncludeHead", true)));
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class InterceptorTestConfiguration {

		static ProducerInterceptor interceptorFoo = mock(ProducerInterceptor.class);
		static ProducerInterceptor interceptorBar = mock(ProducerInterceptor.class);

		@Bean
		@Order(200)
		ProducerInterceptor interceptorFoo() {
			return interceptorFoo;
		}

		@Bean
		@Order(100)
		ProducerInterceptor interceptorBar() {
			return interceptorBar;
		}

	}

}
