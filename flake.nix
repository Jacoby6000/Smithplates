{
  description = "Smithplates development and test environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  nixConfig = {
    extra-experimental-features = "nix-command flakes";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = false;
        };

        java = pkgs.jdk11_headless;

        sbtn = pkgs.writeShellScriptBin "sbtn" ''
          exec ${pkgs.sbt}/bin/sbt --client "$@"
        '';

        runTestsScript = pkgs.writeShellScriptBin "smithystache-run-tests" ''
          set -euo pipefail
          if [ -f ./scripts/run-tests.sh ]; then
            :
          elif command -v git >/dev/null 2>&1 && git rev-parse --show-toplevel >/dev/null 2>&1; then
            cd "$(git rev-parse --show-toplevel)"
          else
            echo "error: run from the Smithplates repository root" >&2
            exit 1
          fi
          exec ./scripts/run-tests.sh "$@"
        '';

        runLintersScript = pkgs.writeShellScriptBin "smithystache-run-linters" ''
          set -euo pipefail
          if [ -f ./scripts/run-linters.sh ]; then
            :
          elif command -v git >/dev/null 2>&1 && git rev-parse --show-toplevel >/dev/null 2>&1; then
            cd "$(git rev-parse --show-toplevel)"
          else
            echo "error: run from the Smithplates repository root" >&2
            exit 1
          fi
          exec ./scripts/run-linters.sh "$@"
        '';

      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            java
            sbtn
            sbt
            uv
            docker
            git
            pre-commit
            python3
          ];

          env = {
            JAVA_HOME = java;
          };

          shellHook = ''
            export JAVA_HOME="${java}"
            echo "Smithplates dev shell (Java 11, sbtn, uv, docker client)"
            echo "  ./validate                    # lint + test (Nix or Docker)"
            echo "  ./scripts/run-linters.sh      # Scala + template linters/compilers"
            echo "  ./scripts/run-tests.sh        # all Scala + Python template tests"
            echo "  ./scripts/run-example-build.sh # regenerate example reference projects"
            echo "  nix run .#run-linters         # linters via flake app"
            echo "  nix run .#run-tests           # tests via flake app"
          '';
        };

        apps.run-linters = {
          type = "app";
          program = "${runLintersScript}/bin/smithystache-run-linters";
        };

        apps.run-tests = {
          type = "app";
          program = "${runTestsScript}/bin/smithystache-run-tests";
        };

        packages.run-linters = runLintersScript;
        packages.run-tests = runTestsScript;
      });
}
