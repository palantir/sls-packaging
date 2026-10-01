# Select launcher binary for this OS
case "`uname -sm`" in
  "Linux x86_64")
    NATIVE_ARCH="linux-amd64"
    ;;
  "Linux aarch64")
    NATIVE_ARCH="linux-arm64"
    ;;
  Darwin*)
    NATIVE_ARCH="darwin-amd64"
    ;;
  *)
    echo "Unsupported operating system: $(uname)"; exit 1
esac

LAUNCHER_CMD="service/bin/${NATIVE_ARCH}/go-java-launcher"
GO_INIT_CMD="service/bin/${NATIVE_ARCH}/go-init"
