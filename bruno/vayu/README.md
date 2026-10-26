# vayu Bruno collection

The collection covers the main API flow:

1. Create an endpoint.
2. List endpoints.
3. Create an event.
4. List deliveries for the endpoint.
5. Get one delivery with its attempts.

## Run it

Start the stack:

```bash
docker compose up --build
```

Open this directory in Bruno and select the `Local` environment. The collection uses `http://localhost:8080/api/v1` as its base URL. The folders are numbered so a recursive CLI run executes them in order.

You can also run the collection with the Bruno CLI:

```bash
bru run . -r --env Local
```

The CLI is optional. Start a receiver on port 9090 before running the event request, or change `receiverUrl` in the `Local` environment.

The receiver URL is controlled by `receiverUrl` in the environment. The default is `http://localhost:9090/webhook`. If the application runs in Docker and the receiver runs on your host, change it to `http://host.docker.internal:9090/webhook`.

Run the requests in order. The create endpoint request stores `endpointId`, `endpointSecret` and the list deliveries request stores `deliveryId` for the following request.

Change `idempotencyKey` before creating a different event. Reusing the same key with a different payload returns `409`.
