FROM golang:latest AS builder
WORKDIR /build

RUN go install go.opentelemetry.io/collector/cmd/builder@v0.123.0

COPY go.mod go.sum ./
COPY config.go exporter.go factory.go ./
COPY ocb-config.yaml ./

RUN builder --config ocb-config.yaml --skip-strict-versioning

FROM alpine:latest
RUN apk --no-cache add ca-certificates

COPY --from=builder /build/dist/lofi-otelcol /lofi-otelcol
COPY collector-config.yaml /etc/otelcol/config.yaml

EXPOSE 4317 4318

ENTRYPOINT ["/lofi-otelcol", "--config", "/etc/otelcol/config.yaml"]
