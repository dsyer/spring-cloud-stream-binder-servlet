/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.stream.binder.servlet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Dave Syer
 *
 */
@RestController
@RequestMapping("/stream")
public class MessageController {

	private ConcurrentMap<String, BlockingQueue<Message<?>>> queues = new ConcurrentHashMap<>();

	private MessageChannel input;

	private Map<String, String> outputs = new HashMap<>();

	private EnabledBindings bindings;

	public MessageController(EnabledBindings bindings) {
		this.bindings = bindings;
	}

	@GetMapping("/{path}")
	public ResponseEntity<List<Object>> supplier(@PathVariable String path) {
		return ResponseEntity.ok(poll(path));
	}

	@PostMapping(path = "/{path}", consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<Collection<Object>> string(@PathVariable String path,
			@RequestBody String body) {
		return consumer(path, body);
	}

	@PostMapping("/{path}")
	public ResponseEntity<Collection<Object>> consumer(@PathVariable String path,
			@RequestBody Object body) {
		Collection<Object> collection;
		if (body instanceof Collection) {
			@SuppressWarnings("unchecked")
			Collection<Object> list = (Collection<Object>) body;
			collection = list;
		}
		else {
			if (ObjectUtils.isArray(body)) {
				collection = Arrays.asList(ObjectUtils.toObjectArray(body));
			}
			else {
				collection = Arrays.asList(body);
			}
		}
		for (Object payload : collection) {
			input.send(MessageBuilder.withPayload(payload).build());
		}
		if (this.outputs.containsKey(path)) {
			return ResponseEntity.ok().body(poll(outputs.get(path)));
		}
		return ResponseEntity.accepted().body(collection);
	}

	private List<Object> poll(String path) {
		List<Object> list = new ArrayList<>();
		List<Message<?>> messages = new ArrayList<>();
		BlockingQueue<Message<?>> queue = queues.get(path);
		if (queue != null) {
			queue.drainTo(messages);
			for (Message<?> message : messages) {
				list.add(message.getPayload());
			}
		}
		return list;
	}

	public void subscribe(String name, SubscribableChannel outboundBindTarget) {
		this.outputs.put(bindings.getInput(name), name);
		outboundBindTarget.subscribe(message -> this.append(name, message));
	}

	private void append(String name, Message<?> message) {
		if (!queues.containsKey(name)) {
			queues.putIfAbsent(name, new LinkedBlockingQueue<>());
		}
		queues.get(name).add(message);
	}

	public void bind(String name, String group, MessageChannel inputTarget) {
		this.input = inputTarget;
	}

}
