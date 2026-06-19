from typing import Self

class PostgresContainer:
    username: str
    password: str
    dbname: str

    def __init__(self, image: str, **kwargs: object) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_val: BaseException | None,
        exc_tb: object,
    ) -> None: ...
    def get_container_host_ip(self) -> str: ...
    def get_exposed_port(self, port: int) -> str: ...
