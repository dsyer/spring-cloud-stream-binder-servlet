/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.stream.binder.servlet.config;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.binder.servlet.EnabledBindings;
import org.springframework.cloud.stream.binder.servlet.MessageController;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Dave Syer
 */
@Configuration
@AutoConfigureBefore({ WebMvcAutoConfiguration.class })
@ConfigurationProperties("spring.cloud.stream.binder.servlet")
public class MessageHandlingAutoConfiguration {

	/**
	 * The prefix for the HTTP endpoint.
	 */
	private String prefix = "stream";

	/**
	 * The buffer timeout for messages sent to output channels, and accessed via GET.
	 */
	private long timeoutSeconds = 10;

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public long getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(long timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	@Bean
	public MessageController messageController(EnabledBindings bindings) {
		MessageController controller = new MessageController(prefix, bindings);
		controller.setTimeoutSeconds(timeoutSeconds);
		return controller;
	}

	@Bean
	public BeanFactoryEnabledBindings enabledBindings(
			ConfigurableListableBeanFactory beanFactory,
			BindingServiceProperties binding) {
		return new BeanFactoryEnabledBindings(beanFactory, binding);
	}
}
