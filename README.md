A Spring Cloud Stream binder for a Servlet container using Spring MVC.

## Usage

Add this jar to the classpath of a Spring Cloud Stream application as the only binder implementation. Endpoints are exposed at

| Method    | Path          | Description                |
|-----------|---------------|----------------------------|
| GET       | /stream/{path}| Returns a list of the payloads of all messages sent to the `@Output` called `{path}` |
| POST      | /stream/{path}| Accepts a single value or a list of payloads and sends them to the `@Input` called `{path}` |

The result of the POST depends on whether an `@Output` is linked to the `@Input`. By default a link is made if the user has `@EnableBinding` with an interface having precisely one `@Output` and one `@Input` (e.g. using `Processor` from Spring Cloud Stream).  In the case that there is no linked `@Output`, the return value from the POST is a 202 (Accepted) and a mirror of the input. If an `@Output` is linked, then the contents of the output channel are returned with a 200 status (OK).
