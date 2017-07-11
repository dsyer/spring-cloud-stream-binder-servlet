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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @author Dave Syer
 *
 */
@RestController
@RequestMapping("/${spring.cloud.stream.binder.servlet.prefix:stream}")
public class MessageController {

	public static final String ROUTE_KEY = "stream_routekey";

	private static final int BUFFER_SIZE = 100;

	private ConcurrentMap<String, BlockingQueue<Message<?>>> queues = new ConcurrentHashMap<>();

	private ConcurrentMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

	private Map<String, MessageChannel> inputs = new HashMap<>();

	private Map<String, String> outputs = new HashMap<>();

	private EnabledBindings bindings;

	private String prefix;

	private ThreadLocal<Route> threadLocalRoute = new ThreadLocal<>();

	public MessageController(String prefix, EnabledBindings bindings) {
		if (!prefix.startsWith("/")) {
			prefix = "/" + prefix;
		}
		if (!prefix.endsWith("/")) {
			prefix = prefix + "/";
		}
		this.prefix = prefix;
		this.bindings = bindings;
	}

	@GetMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> sse(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestHeader HttpHeaders headers) throws IOException {
		Route route = new Route(path);
		String channel = route.getChannel();
		if (!bindings.getOutputs().contains(channel)) {
			return org.springframework.http.ResponseEntity.notFound().build();
		}
		Message<Collection<Object>> message = poll(route.getChannel(), route.getKey(),
				true);
		SseEmitter body = emit(route, message);
		return ResponseEntity.ok()
				.headers(HeaderUtils.fromMessage(message.getHeaders(), headers))
				.body(body);
	}

	@GetMapping("/**")
	public ResponseEntity<Object> supplier(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestHeader HttpHeaders headers,
			@RequestParam(required = false) boolean purge) {
		Route route = new Route(path);
		String channel = route.getChannel();
		if (!bindings.getOutputs().contains(channel)) {
			return ResponseEntity.notFound().build();
		}
		return convert(poll(channel, route.getKey(), !purge), headers);
	}

	@PostMapping(path = "/**", consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<Object> string(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestBody String body, @RequestHeader HttpHeaders headers) {
		return consumer(path, body, headers);
	}

	@PostMapping(path = "/**", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> json(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestBody String body, @RequestHeader HttpHeaders headers) {
		return consumer(path, extract(body), headers);
	}

	private Object extract(String body) {
		body = body.trim();
		Object result = body;
		if (body.startsWith("[")) {
			result = JsonUtils.split(body);
		}
		return result;
	}

	@PostMapping("/**")
	public ResponseEntity<Object> consumer(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestBody Object body, @RequestHeader HttpHeaders headers) {
		Route route = new Route(path);
		String channel = route.getChannel();
		if (!inputs.containsKey(channel)) {
			return ResponseEntity.notFound().build();
		}
		Collection<Object> collection;
		boolean single = false;
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
				single = true;
				collection = Arrays.asList(body);
			}
		}
		Map<String, Object> messageHeaders = new HashMap<>(HeaderUtils.fromHttp(headers));
		if (route.getKey() != null) {
			messageHeaders.put(ROUTE_KEY, route.getKey());
		}
		MessageChannel input = inputs.get(channel);
		threadLocalRoute.set(route);
		try {
			for (Object payload : collection) {
				input.send(MessageBuilder.withPayload(payload)
						.copyHeadersIfAbsent(messageHeaders).build());
			}
		}
		finally {
			threadLocalRoute.remove();
		}
		if (this.outputs.containsKey(channel)) {
			Message<Collection<Object>> content = poll(outputs.get(channel),
					route.getKey(), false);
			if (!content.getPayload().isEmpty()) {
				Message<?> output = content;
				if (single && content.getPayload().size() == 1) {
					output = MessageBuilder.createMessage(
							content.getPayload().iterator().next(), content.getHeaders());
				}
				return convert(output, headers);
			}
		}
		if (headers.getContentType().includes(MediaType.APPLICATION_JSON)
				&& body.toString().contains("\"")) {
			body = body.toString();
		}
		return convert(HttpStatus.ACCEPTED, MessageBuilder.withPayload(body)
				.copyHeadersIfAbsent(messageHeaders).build(), headers);
	}

	private ResponseEntity<Object> convert(Message<?> message, HttpHeaders request) {
		return convert(HttpStatus.OK, message, request);
	}

	private ResponseEntity<Object> convert(HttpStatus status, Message<?> message,
			HttpHeaders request) {
		return ResponseEntity.status(status)
				.headers(HeaderUtils.fromMessage(message.getHeaders(), request))
				.body(message.getPayload());
	}

	private SseEmitter emit(Route route, Message<Collection<Object>> message)
			throws IOException {
		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
		String path = route.getPath();
		if (!emitters.containsKey(path)) {
			emitters.putIfAbsent(path, new HashSet<>());
		}
		emitters.get(path).add(emitter);
		emitter.onCompletion(() -> emitters.get(path).remove(emitter));
		emitter.onTimeout(() -> emitters.get(path).remove(emitter));
		for (Object body : message.getPayload()) {
			emitter.send(body);
		}
		return emitter;
	}

	private Message<Collection<Object>> poll(String channel, String route,
			boolean requeue) {
		List<Object> list = new ArrayList<>();
		List<Message<?>> messages = new ArrayList<>();
		BlockingQueue<Message<?>> queue = queues.get(new Route(route, channel).getPath());
		if (queue != null) {
			queue.drainTo(messages);
			int count = messages.size() - BUFFER_SIZE;
			for (Message<?> message : messages) {
				list.add(message.getPayload());
				count--;
				if (count < 0 && requeue) {
					queue.offer(message);
				}
			}
		}
		MessageBuilder<Collection<Object>> builder = MessageBuilder.withPayload(list);
		if (!messages.isEmpty()) {
			builder.copyHeadersIfAbsent(messages.get(0).getHeaders());
		}
		return builder.build();
	}

	public void subscribe(String name, SubscribableChannel outboundBindTarget) {
		this.outputs.put(bindings.getInput(name), name);
		outboundBindTarget.subscribe(message -> this.append(name, message));
	}

	private void append(String name, Message<?> message) {
		String incoming = (String) message.getHeaders().get(ROUTE_KEY);
		String key = incoming;
		if (key == null && threadLocalRoute.get() != null) {
			// If we can rescue the header from thread local we will do it. It's a shame
			// that the headers don't get propagated by default.
			key = threadLocalRoute.get().getKey();
		}
		Route route = new Route(key, name);
		String path = route.getPath();
		if (!queues.containsKey(path)) {
			queues.putIfAbsent(path, new LinkedBlockingQueue<>());
		}
		if (incoming == null && key != null) {
			message = MessageBuilder.fromMessage(message).setHeader(ROUTE_KEY, key)
					.build();
		}
		queues.get(path).add(message);
		if (emitters.containsKey(path)) {
			for (SseEmitter emitter : emitters.get(path)) {
				try {
					emitter.send(message.getPayload());
				}
				catch (IOException e) {
					emitters.get(path).remove(emitter);
				}
			}
		}
	}

	public void bind(String name, String group, MessageChannel inputTarget) {
		this.inputs.put(name, inputTarget);
	}

	private class Route {
		private String key;
		private String channel;
		private String path;

		public Route(String path) {
			String channel = path.substring(prefix.length());
			String[] paths = channel.split("/");
			String route = null;
			if (paths.length > 1) {
				channel = paths[paths.length - 1];
				route = path.substring(prefix.length(),
						path.length() - channel.length() - 1);
			}
			this.channel = channel;
			this.key = route;
			this.path = key != null ? key + "/" + channel : channel;
		}

		public Route(String key, String channel) {
			this.key = key;
			this.channel = channel;
			this.path = key != null ? key + "/" + channel : channel;
		}

		public String getPath() {
			return path;
		}

		public String getKey() {
			return key;
		}

		public String getChannel() {
			return channel;
		}
	}
}
