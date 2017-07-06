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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.stream.binder.servlet.EnabledBindings;
import org.springframework.cloud.stream.binder.servlet.MessageController;
import org.springframework.cloud.stream.binder.servlet.ServletMessageChannelBinder;
import org.springframework.cloud.stream.config.codec.kryo.KryoCodecAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.codec.Codec;

/**
 * @author David Turanski
 */
@Configuration
@Import({ PropertyPlaceholderAutoConfiguration.class, KryoCodecAutoConfiguration.class })
public class ServletMessageChannelBinderConfiguration {

	@Autowired
	private Codec codec;

	@Bean
	public ServletMessageChannelBinder servletMessageChannelBinder(
			MessageController controller) {

		ServletMessageChannelBinder messageChannelBinder = new ServletMessageChannelBinder(
				controller);
		messageChannelBinder.setCodec(this.codec);
		return messageChannelBinder;
	}

	@Bean
	public MessageController messageController(EnabledBindings bindings) {
		return new MessageController(bindings);
	}

	@Bean
	public BeanFactoryEnabledBindings enabledBindings(
			ConfigurableListableBeanFactory beanFactory) {
		return new BeanFactoryEnabledBindings(beanFactory);
	}
}
