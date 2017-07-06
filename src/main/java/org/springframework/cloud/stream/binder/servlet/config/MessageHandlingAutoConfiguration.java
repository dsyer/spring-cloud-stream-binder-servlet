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
public class MessageHandlingAutoConfiguration {

	@Bean
	public MessageController messageController(EnabledBindings bindings) {
		return new MessageController(bindings);
	}

	@Bean
	public BeanFactoryEnabledBindings enabledBindings(
			ConfigurableListableBeanFactory beanFactory,
			BindingServiceProperties binding) {
		return new BeanFactoryEnabledBindings(beanFactory, binding);
	}
}
