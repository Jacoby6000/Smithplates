use smithplates_rust_client_tests::generated::{
    client, models,
    runtime::{self, TransportError},
};
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

fn get_input(id: &str) -> models::GetItemInput {
    models::GetItemInput {
        id: id.into(),
        filters: Some(vec!["a & b".into(), "é".into()]),
        request_id: Some("request-1".into()),
    }
}

async fn server(
    status: &str,
    body: &[u8],
    delay: Duration,
) -> (String, tokio::task::JoinHandle<String>) {
    server_with_headers(status, body, delay, "").await
}

async fn server_with_headers(
    status: &str,
    body: &[u8],
    delay: Duration,
    extra_headers: &str,
) -> (String, tokio::task::JoinHandle<String>) {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let body = body.to_vec();
    let status = status.to_owned();
    let extra_headers = extra_headers.to_owned();
    let task = tokio::spawn(async move {
        let (mut stream, _) = listener.accept().await.unwrap();
        let mut request = Vec::new();
        let header_end = loop {
            let mut buffer = [0; 1024];
            let read = stream.read(&mut buffer).await.unwrap();
            assert_ne!(read, 0);
            request.extend_from_slice(&buffer[..read]);
            if let Some(end) = request.windows(4).position(|value| value == b"\r\n\r\n") {
                break end + 4;
            }
        };
        let headers = String::from_utf8_lossy(&request[..header_end]);
        let length = headers
            .lines()
            .find_map(|line| {
                line.to_ascii_lowercase()
                    .strip_prefix("content-length: ")
                    .map(|value| value.parse::<usize>().unwrap())
            })
            .unwrap_or(0);
        while request.len() < header_end + length {
            let mut buffer = [0; 1024];
            let read = stream.read(&mut buffer).await.unwrap();
            assert_ne!(read, 0);
            request.extend_from_slice(&buffer[..read]);
        }
        let content_type = if extra_headers.to_ascii_lowercase().contains("content-type:") {
            ""
        } else {
            "Content-Type: application/json\r\n"
        };
        let response = format!(
            "HTTP/1.1 {status}\r\nContent-Length: {}\r\n{content_type}Location: http://127.0.0.1:{}/other\r\n{extra_headers}Connection: close\r\n\r\n",
            body.len(),
            address.port()
        );
        stream.write_all(response.as_bytes()).await.unwrap();
        tokio::time::sleep(delay).await;
        let _ = stream.write_all(&body).await;
        drop(stream);
        assert!(
            tokio::time::timeout(Duration::from_millis(30), listener.accept())
                .await
                .is_err(),
            "unexpected retry/redirect"
        );
        String::from_utf8(request).unwrap()
    });
    (format!("http://{address}/base/"), task)
}

const ITEM: &[u8] =
    br#"{"id":"item-1","wireName":"name","state":"available","choice":{"wireText":"hello"}}"#;

#[tokio::test]
async fn labels_queries_headers_and_base_path() {
    let (url, request) = server("200 OK", ITEM, Duration::ZERO).await;
    let client = client::Client::new(&url).unwrap();
    let item = client.get_item(&get_input("a/b ?#é"), None).await.unwrap();
    assert_eq!(item.id, "item-1");
    let request = request.await.unwrap();
    assert!(
        request.starts_with(
            "GET /base/items/a%2Fb%20%3F%23%C3%A9?filter=a+%26+b&filter=%C3%A9 HTTP/1.1\r\n"
        ),
        "{request}"
    );
    assert!(request.contains("x-request-id: request-1\r\n"));
    assert!(request.contains("x-contract: catalog\r\n"));
    assert!(!request.contains("authorization:"));
}

#[tokio::test]
async fn document_body_removes_transport_members_and_uses_wire_names() {
    let (url, request) = server("200 OK", ITEM, Duration::ZERO).await;
    let input: models::PutItemInput = serde_json::from_value(serde_json::json!({
        "id": "item-1", "wireName": "value", "state": "unavailable", "choice": {"number": 42},
        "metadata": {"key":"value"}, "document": [true, 3], "count": 5, "active": false, "sequence": 9000000000_i64
    })).unwrap();
    client::Client::new(&url)
        .unwrap()
        .put_item(&input, None)
        .await
        .unwrap();
    let request = request.await.unwrap();
    let body: serde_json::Value =
        serde_json::from_str(request.split("\r\n\r\n").nth(1).unwrap()).unwrap();
    assert!(body.get("id").is_none());
    assert_eq!(body["wireName"], "value");
    assert_eq!(body["choice"]["number"], 42);
    assert_eq!(body["active"], false);
}

#[tokio::test]
async fn service_error_is_status_directed_and_diagnostics_are_redacted() {
    let (url, request) = server_with_headers("404 Not Found", br#"{"type":"urn:problem:not-found","title":"Not found","detail":"secret","message":"credential"}"#, Duration::ZERO, "Content-Type: application/problem+json\r\n").await;
    let error = client::Client::new(&url)
        .unwrap()
        .get_item(&get_input("id"), None)
        .await
        .err()
        .unwrap();
    assert_eq!(format!("{error}"), "NotFound");
    assert_eq!(format!("{error:?}"), "NotFound");
    match error {
        client::GetItemError::NotFound(problem) => {
            assert_eq!(problem.message.as_deref(), Some("credential"));
            assert_eq!(problem.http_problem.detail.as_deref(), Some("secret"));
        }
        _ => panic!("expected typed error"),
    }
    request.await.unwrap();
}

#[tokio::test]
async fn empty_success_and_exact_status() {
    let (url, request) = server("204 No Content", b"", Duration::ZERO).await;
    client::Client::new(&url)
        .unwrap()
        .delete_item(&models::DeleteItemInput { id: "id".into() }, None)
        .await
        .unwrap();
    request.await.unwrap();
    let (url, request) = server("201 Created", ITEM, Duration::ZERO).await;
    let error = client::Client::new(&url)
        .unwrap()
        .get_item(&get_input("id"), None)
        .await
        .err()
        .unwrap();
    assert!(matches!(
        error,
        client::GetItemError::Client(TransportError::UnexpectedStatus(201))
    ));
    request.await.unwrap();
}

#[tokio::test]
async fn malformed_missing_and_wrong_json_types_are_safe_errors() {
    for body in [
        b"secret".as_slice(),
        b"{}",
        br#"{"id":3,"wireName":"name","state":"available"}"#,
        b"",
    ] {
        let (url, request) = server("200 OK", body, Duration::ZERO).await;
        let error = client::Client::new(&url)
            .unwrap()
            .get_item(&get_input("id"), None)
            .await
            .err()
            .unwrap();
        assert!(matches!(
            error,
            client::GetItemError::Client(TransportError::InvalidJson)
        ));
        assert!(!format!("{error:?}").contains("secret"));
        request.await.unwrap();
    }
}

#[tokio::test]
async fn bounded_response_and_no_redirects_or_retries() {
    let (url, request) = server("200 OK", ITEM, Duration::ZERO).await;
    let mut client = client::Client::new(&url).unwrap();
    client.runtime.max_response_bytes = 8;
    assert!(matches!(
        client.get_item(&get_input("id"), None).await.err().unwrap(),
        client::GetItemError::Client(TransportError::ResponseTooLarge)
    ));
    request.await.unwrap();
    for status in ["302 Found", "503 Unavailable"] {
        let (url, request) = server(status, b"", Duration::ZERO).await;
        let error = client::Client::new(&url)
            .unwrap()
            .get_item(&get_input("id"), None)
            .await
            .err()
            .unwrap();
        assert!(matches!(
            error,
            client::GetItemError::Client(TransportError::UnexpectedStatus(_))
        ));
        request.await.unwrap();
    }
}

#[tokio::test]
async fn timeout_covers_slow_response_body() {
    let (url, request) = server("200 OK", ITEM, Duration::from_millis(100)).await;
    let error = client::Client::new(&url)
        .unwrap()
        .get_item(&get_input("id"), Some(Duration::from_millis(20)))
        .await
        .err()
        .unwrap();
    assert!(matches!(
        error,
        client::GetItemError::Client(TransportError::Timeout)
    ));
    request.await.unwrap();
}

#[tokio::test]
async fn dropping_future_stops_waiting_not_server_work() {
    let (url, request) = server("200 OK", ITEM, Duration::from_millis(100)).await;
    let client = client::Client::new(&url).unwrap();
    let input = get_input("id");
    {
        let work = client.get_item(&input, None);
        tokio::pin!(work);
        tokio::select! {
            result = &mut work => panic!("completed early: {}", result.is_ok()),
            () = tokio::time::sleep(Duration::from_millis(20)) => {}
        }
    }
    assert!(request.await.unwrap().starts_with("GET "));
}

#[test]
fn enum_union_and_keyword_wire_roundtrips() {
    for choice in [
        serde_json::json!({"wireText":"hello"}),
        serde_json::json!({"number":7}),
    ] {
        let value: models::Choice = serde_json::from_value(choice.clone()).unwrap();
        assert_eq!(serde_json::to_value(value).unwrap(), choice);
    }
    for state in ["available", "unavailable"] {
        let value: models::State = serde_json::from_value(serde_json::json!(state)).unwrap();
        assert_eq!(serde_json::to_value(value).unwrap(), state);
    }
    assert!(serde_json::from_value::<models::State>(serde_json::json!("other")).is_err());
    let value: models::Item = serde_json::from_value(
        serde_json::json!({"id":"id","wireName":"name","state":"available","type":"custom"}),
    )
    .unwrap();
    assert_eq!(value.type_.as_deref(), Some("custom"));
    assert_eq!(serde_json::to_value(value).unwrap()["type"], "custom");
    assert_eq!(client::SERVICE_ID, "example#Catalog");
    assert_eq!(client::SERVICE_VERSION, "2026-10-09");
    for number in -1000..=1000 {
        let wire = serde_json::json!({"number": number});
        let value: models::Choice = serde_json::from_value(wire.clone()).unwrap();
        assert_eq!(serde_json::to_value(value).unwrap(), wire);
    }
}

#[tokio::test]
async fn invalid_labels_and_header_injection_fail_before_io() {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let client =
        client::Client::new(&format!("http://{}", listener.local_addr().unwrap())).unwrap();
    for id in ["", ".", ".."] {
        assert!(matches!(
            client.get_item(&get_input(id), None).await.err().unwrap(),
            client::GetItemError::Client(TransportError::InvalidBinding)
        ));
    }
    let mut input = get_input("id");
    input.request_id = Some("value\r\nX-Injected: secret".into());
    let error = client.get_item(&input, None).await.err().unwrap();
    assert!(matches!(
        error,
        client::GetItemError::Client(TransportError::InvalidBinding)
    ));
    assert!(!format!("{error:?}").contains("secret"));
    assert!(
        tokio::time::timeout(Duration::from_millis(30), listener.accept())
            .await
            .is_err()
    );
}

#[test]
fn base_url_rejects_credentials_queries_fragments_and_other_schemes() {
    for url in [
        "http://user:secret@localhost",
        "http://localhost/?secret=1",
        "http://localhost/#secret",
        "file:///secret",
        "not a URL",
    ] {
        assert!(matches!(
            client::Client::new(url),
            Err(TransportError::InvalidBaseUrl)
        ));
    }
}

struct Credentials {
    scheme: &'static str,
    value: &'static str,
}

impl runtime::AuthProvider for Credentials {
    fn credential(
        &self,
        operation_id: &str,
        scheme_id: &str,
    ) -> Result<Option<String>, TransportError> {
        assert!(operation_id.starts_with("example#"));
        Ok((scheme_id == self.scheme).then(|| self.value.into()))
    }
}

#[tokio::test]
async fn required_credentials_fail_before_io() {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let client =
        client::Client::new(&format!("http://{}", listener.local_addr().unwrap())).unwrap();
    assert!(matches!(
        client.secured(None).await.err().unwrap(),
        client::SecuredError::Client(TransportError::MissingCredentials)
    ));
    assert!(
        tokio::time::timeout(Duration::from_millis(30), listener.accept())
            .await
            .is_err()
    );
}

#[tokio::test]
async fn credential_alternatives_headers_cookies_optional_and_public() {
    for (scheme, expected, operation) in [
        (
            "smithy.api#httpBearerAuth",
            "authorization: Bearer secret\r\n",
            "secured",
        ),
        (
            "smithy.api#httpApiKeyAuth",
            "x-api-key: ApiKey secret\r\n",
            "api_key",
        ),
        (
            "smithplates.codegen.http#httpCookieAuth",
            "cookie: session=secret\r\n",
            "cookie",
        ),
        (
            "smithy.api#httpApiKeyAuth",
            "x-api-key: ApiKey secret\r\n",
            "secured",
        ),
    ] {
        let (url, request) = server("200 OK", ITEM, Duration::ZERO).await;
        let mut client = client::Client::new(&url).unwrap();
        client.auth_provider = Some(std::sync::Arc::new(Credentials {
            scheme,
            value: "secret",
        }));
        match operation {
            "api_key" => {
                client.api_key(None).await.unwrap();
            }
            "cookie" => {
                client.cookie(None).await.unwrap();
            }
            _ => {
                client.secured(None).await.unwrap();
            }
        }
        assert!(request.await.unwrap().contains(expected));
    }
    let (url, request) = server("200 OK", ITEM, Duration::ZERO).await;
    client::Client::new(&url)
        .unwrap()
        .optional(None)
        .await
        .unwrap();
    assert!(!request.await.unwrap().contains("authorization:"));
    let (url, request) = server("200 OK", ITEM, Duration::ZERO).await;
    let mut client = client::Client::new(&url).unwrap();
    client.auth_provider = Some(std::sync::Arc::new(Credentials {
        scheme: "smithy.api#httpBearerAuth",
        value: "secret",
    }));
    client.get_item(&get_input("id"), None).await.unwrap();
    assert!(!request.await.unwrap().contains("secret"));
}

#[tokio::test]
async fn query_credentials_are_encoded_and_contract_collisions_fail_before_io() {
    let (url, request) = server("200 OK", ITEM, Duration::ZERO).await;
    let client = runtime::Client::new(&url).unwrap();
    let spec = runtime::RequestSpec {
        method: "GET",
        path: "/query",
        bindings: &[],
        body: runtime::Body::None,
        static_headers: &[],
        operation_id: "example#Query",
        requires_auth: true,
        auth: &[runtime::AuthScheme {
            id: "smithy.api#httpApiKeyAuth",
            location: "query",
            name: "api_key",
            prefix: None,
        }],
    };
    runtime::execute(
        &client,
        spec,
        serde_json::json!({}),
        None,
        Some(&Credentials {
            scheme: "smithy.api#httpApiKeyAuth",
            value: "a & b",
        }),
    )
    .await
    .unwrap();
    assert!(
        request
            .await
            .unwrap()
            .starts_with("GET /base/query?api_key=a+%26+b HTTP/1.1\r\n")
    );

    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let client =
        runtime::Client::new(&format!("http://{}", listener.local_addr().unwrap())).unwrap();
    let spec = runtime::RequestSpec {
        method: "GET",
        path: "/query",
        bindings: &[],
        body: runtime::Body::None,
        static_headers: &[("Authorization", "contract")],
        operation_id: "example#Query",
        requires_auth: true,
        auth: &[runtime::AuthScheme {
            id: "smithy.api#httpBearerAuth",
            location: "header",
            name: "Authorization",
            prefix: Some("Bearer"),
        }],
    };
    let result = runtime::execute(
        &client,
        spec,
        serde_json::json!({}),
        None,
        Some(&Credentials {
            scheme: "smithy.api#httpBearerAuth",
            value: "secret",
        }),
    )
    .await;
    assert!(matches!(result, Err(TransportError::InvalidBinding)));
    assert!(
        tokio::time::timeout(Duration::from_millis(30), listener.accept())
            .await
            .is_err()
    );
}

#[tokio::test]
async fn nested_json_payload_is_not_wrapped() {
    let (url, request) = server("200 OK", ITEM, Duration::ZERO).await;
    let input = models::NestedInput {
        id: "route-id".into(),
        body: serde_json::from_slice(ITEM).unwrap(),
    };
    client::Client::new(&url)
        .unwrap()
        .put_nested(&input, None)
        .await
        .unwrap();
    let request = request.await.unwrap();
    let body: serde_json::Value =
        serde_json::from_str(request.split("\r\n\r\n").nth(1).unwrap()).unwrap();
    assert_eq!(body["id"], "item-1");
    assert!(body.get("body").is_none());
}

#[tokio::test]
async fn operation_error_is_typed_and_redacted() {
    let (url, request) = server("409 Conflict", br#"{"message":"secret"}"#, Duration::ZERO).await;
    let input: models::PutItemInput = serde_json::from_value(
        serde_json::json!({"id":"id","wireName":"name","state":"available"}),
    )
    .unwrap();
    let error = client::Client::new(&url)
        .unwrap()
        .put_item(&input, None)
        .await
        .err()
        .unwrap();
    assert_eq!(format!("{error:?}"), "Conflict");
    match error {
        client::PutItemError::Conflict(payload) => assert_eq!(payload.message, "secret"),
        _ => panic!("expected operation error"),
    }
    request.await.unwrap();
}

#[tokio::test]
async fn response_headers_override_body_and_validate_required_scalar_types() {
    let (url, request) = server_with_headers(
        "200 OK",
        br#"{"value":"value","count":999,"trace":"spoofed"}"#,
        Duration::ZERO,
        "X-Count: 42\r\nX-Contract: catalog\r\n",
    )
    .await;
    let result = client::Client::new(&url)
        .unwrap()
        .read_headers(None)
        .await
        .unwrap();
    assert_eq!(result.count, 42);
    assert!(result.trace.is_none());
    request.await.unwrap();
    for headers in [
        "X-Contract: catalog\r\n",
        "X-Count: wrong\r\nX-Contract: catalog\r\n",
        "X-Count: 42\r\nX-Count: 43\r\nX-Contract: catalog\r\n",
        "X-Count: 42\r\nX-Contract: wrong\r\n",
    ] {
        let (url, request) =
            server_with_headers("200 OK", br#"{"value":"value"}"#, Duration::ZERO, headers).await;
        assert!(
            client::Client::new(&url)
                .unwrap()
                .read_headers(None)
                .await
                .is_err()
        );
        request.await.unwrap();
    }
    let (url, request) =
        server_with_headers("200 OK", b"", Duration::ZERO, "X-Count: 9000000000\r\n").await;
    let result = client::Client::new(&url)
        .unwrap()
        .read_header_only(None)
        .await
        .unwrap();
    assert_eq!(result.count, 9000000000);
    request.await.unwrap();
}
