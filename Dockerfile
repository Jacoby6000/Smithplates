# syntax=docker/dockerfile:1

FROM nixos/nix:2.24.7

RUN mkdir -p /etc/nix && \
    echo "experimental-features = nix-command flakes" >> /etc/nix/nix.conf && \
    echo "filter-syscalls = false" >> /etc/nix/nix.conf

WORKDIR /smithystache

# Warm the Nix store with dev-shell tooling only; sources are bind-mounted at run time.
COPY flake.nix flake.lock ./

RUN nix develop --accept-flake-config -c true

ENTRYPOINT ["nix", "develop", "--accept-flake-config", "-c"]
CMD ["./scripts/run-tests.sh", "all"]
