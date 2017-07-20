A Spring Cloud Stream binder for a Servlet container using Spring MVC.

## Usage

Add this jar to the classpath of a Spring Cloud Stream application as the only binder implementation. Endpoints are exposed at

| Method    | Path                               | Description                |
|-----------|------------------------------------|----------------------------|
| GET       | `/stream/{channel}/{route}`        | If the channel is an `@Output` returns a list of the payloads of all messages sent to the channel with a routing key equal to `{route}`. |
| GET       | `/stream/{channel}/{route}/{body}` | If the channel is an `@Input` sends the last segment of the path (the `{body}`) as a payload to the `{route}`. If the channel is not an input, it's an error (404). |
| POST      | `/stream/{channel}/{route}`        | Accepts a single value or a list of payloads and sends them to the `@Input` called `{channel}` with routing key header equal to `{route}`.|

The result of the POST depends on whether an `@Output` is linked to the `@Input`. By default a link is made if the user has `@EnableBinding` with an interface having precisely one `@Output` and one `@Input` (e.g. using `Processor` from Spring Cloud Stream).  In the case that there is no linked `@Output`, the return value from the POST is a 202 (Accepted) and a mirror of the input. If an `@Output` is linked, then the contents of the output channel are returned with a 200 status (OK).

The routing key is sent via a message header named `stream_routekey`. It will show up in HTTP response headers if present.

Both the channel and route are optional, but can be used to disambiguate if necessary. So for example:

| Method    | Path                     | Description                |
|-----------|--------------------------|----------------------------|
| GET       | `/stream`                | Defaults the channel name to `output` (or to the name of the `@Output` if there is only one). Messages sent with no routing key are delivered. |
| GET       | `/stream/{channel}`      | Uses and explicit channel name but an empty routing key. |
| GET       | `/stream/{route}/{body}` | If the channel is not a registered output, then it will be interpreted as a route. Equivalent to `GET /stream/input/{route}/{body}` if there is a unique input channel. |
| GET       | `/stream/{body}`         | If the channel and route are missing (i.e. the first and only path segment is not a registered output), then it will be interpreted as a body. Equivalent to `GET /stream/input/{body}` if there is a unique input channel. |
| POST      | `/stream/{route}`        | As long as the route is not an input channel name, this defaults the channel to `input` (or to the name of the `@Input` if it is unique).|
| POST      | `/stream`                | Defaults the channel to `input` (or to the name of the `@Input` if it is unique). The routing key is empty.|


Note that with a GET, if the channel is not a registered output, then it will be interpreted as a route. So if there is a default input channel, then the path will be transformed into `{route}/{body}` (agin with route optional, if there is only one path segment) and sent to the input channel.

The result of a GET is a moving time window by default (the last 10 seconds of data are buffered). Clients can request an infinite stream of data using `GET` with `Accept: text/event-stream` (or a compatible media type).
