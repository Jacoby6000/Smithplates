# syntax=docker/dockerfile:1

FROM nixos/nix:2.24.7

RUN mkdir -p /etc/nix && \
    echo "experimental-features = nix-command flakes" >> /etc/nix/nix.conf && \
    echo "filter-syscalls = false" >> /etc/nix/nix.conf

WORKDIR /smithystache

COPY flake.nix flake.lock ./
COPY scripts/run-tests.sh scripts/
COPY language-test-harnesses/python/pyproject.toml language-test-harnesses/python/uv.lock language-test-harnesses/python/

RUN nix develop --accept-flake-config -c true

COPY . .

RUN nix develop --accept-flake-config -c true

ENTRYPOINT ["nix", "develop", "--accept-flake-config", "-c"]
CMD ["./scripts/run-tests.sh", "all"]
