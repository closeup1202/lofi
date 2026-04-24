package lofiexporter

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"go.opentelemetry.io/collector/pdata/ptrace"
	"go.uber.org/zap"
)

// commitHashAttrs is the list of resource attributes checked (in order) to find the deploy commit hash.
var commitHashAttrs = []string{
	"deployment.commit.hash", // lofi convention
	"service.version",        // fallback: standard OTEL attribute
}

type lofiExporter struct {
	config          *Config
	httpClient      *http.Client
	logger          *zap.Logger
	excludeMatchers []packageMatcher
}

// packageMatcher is a pre-parsed form of one lofi.exclude_packages entry.
// We compile these once at construction so ConsumeTraces stays allocation-free
// on the span hot path.
type packageMatcher struct {
	raw         string
	matchAll    bool   // set when pattern == "*"
	wildcardBase string // non-empty when pattern ends with ".*"; base = pattern without trailing ".*"
	prefix      string // non-empty when pattern has no wildcard (legacy HasPrefix)
}

func (m packageMatcher) matches(className string) bool {
	switch {
	case m.matchAll:
		return true
	case m.wildcardBase != "":
		return className == m.wildcardBase || strings.HasPrefix(className, m.wildcardBase+".")
	case m.prefix != "":
		return strings.HasPrefix(className, m.prefix)
	default:
		return false
	}
}

func compileMatchers(patterns []string) []packageMatcher {
	if len(patterns) == 0 {
		return nil
	}
	out := make([]packageMatcher, 0, len(patterns))
	for _, raw := range patterns {
		p := strings.TrimSpace(raw)
		if p == "" {
			continue
		}
		switch {
		case p == "*":
			out = append(out, packageMatcher{raw: raw, matchAll: true})
		case strings.HasSuffix(p, ".*"):
			base := strings.TrimSuffix(p, ".*")
			if base == "" {
				continue
			}
			out = append(out, packageMatcher{raw: raw, wildcardBase: base})
		default:
			out = append(out, packageMatcher{raw: raw, prefix: p})
		}
	}
	return out
}

func isExcluded(matchers []packageMatcher, className string) bool {
	for i := range matchers {
		if matchers[i].matches(className) {
			return true
		}
	}
	return false
}

// methodMetric mirrors lofi-backend's MethodMetric domain object.
type methodMetric struct {
	ClassName  string    `json:"className"`
	MethodName string    `json:"methodName"`
	ElapsedNs  int64     `json:"elapsedNs"`
	RecordedAt time.Time `json:"recordedAt"`
}

// ingestRequest mirrors lofi-backend's IngestRequest.
type ingestRequest struct {
	CommitHash string         `json:"commitHash"`
	Metrics    []methodMetric `json:"metrics"`
}

func newLofiExporter(cfg *Config, logger *zap.Logger) *lofiExporter {
	return &lofiExporter{
		config:          cfg,
		httpClient:      &http.Client{Timeout: 10 * time.Second},
		logger:          logger,
		excludeMatchers: compileMatchers(cfg.ExcludePackages),
	}
}

// ConsumeTraces is called by the Collector for every batch of spans.
func (e *lofiExporter) ConsumeTraces(ctx context.Context, td ptrace.Traces) error {
	// Group metrics by commit hash across all resource spans
	byCommit := make(map[string][]methodMetric)

	rss := td.ResourceSpans()
	for i := 0; i < rss.Len(); i++ {
		rs := rss.At(i)

		commitHash := extractCommitHash(rs)
		if commitHash == "" {
			e.logger.Debug("skipping resource spans: no commit hash attribute found")
			continue
		}

		sss := rs.ScopeSpans()
		for j := 0; j < sss.Len(); j++ {
			spans := sss.At(j).Spans()
			for k := 0; k < spans.Len(); k++ {
				span := spans.At(k)

				// Only process server or internal spans (skip client/producer calls)
				kind := span.Kind()
				if kind != ptrace.SpanKindServer && kind != ptrace.SpanKindInternal {
					continue
				}

				className, methodName := parseSpanName(span.Name())
				if className == "" || methodName == "" {
					continue
				}

				// User-configured exclusion. Mirrors the Actuator-mode
				// `lofi.exclude-packages` behavior so self-monitoring/ops
				// endpoints don't inflate metric counts in Backend mode either.
				if isExcluded(e.excludeMatchers, className) {
					continue
				}

				elapsedNs := int64(span.EndTimestamp() - span.StartTimestamp())
				if elapsedNs <= 0 {
					continue
				}

				byCommit[commitHash] = append(byCommit[commitHash], methodMetric{
					ClassName:  className,
					MethodName: methodName,
					ElapsedNs:  elapsedNs,
					RecordedAt: span.EndTimestamp().AsTime(),
				})
			}
		}
	}

	// Send one ingest request per commit hash
	for commitHash, metrics := range byCommit {
		if err := e.send(ctx, commitHash, metrics); err != nil {
			e.logger.Error("failed to ingest metrics", zap.String("commitHash", commitHash), zap.Error(err))
		}
	}
	return nil
}

func (e *lofiExporter) send(ctx context.Context, commitHash string, metrics []methodMetric) error {
	body, err := json.Marshal(ingestRequest{CommitHash: commitHash, Metrics: metrics})
	if err != nil {
		return fmt.Errorf("marshal ingest request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, e.config.BackendURL+"/lofi/ingest", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Lofi-Api-Key", e.config.APIKey)

	resp, err := e.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("send request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("unexpected status: %d", resp.StatusCode)
	}

	e.logger.Debug("ingested metrics", zap.String("commitHash", commitHash), zap.Int("count", len(metrics)))
	return nil
}

// extractCommitHash looks for a deploy commit hash in the resource attributes.
func extractCommitHash(rs ptrace.ResourceSpans) string {
	attrs := rs.Resource().Attributes()
	for _, key := range commitHashAttrs {
		if val, ok := attrs.Get(key); ok {
			return val.AsString()
		}
	}
	return ""
}

// parseSpanName parses "ClassName.methodName" into its two parts.
// Returns empty strings if the format is not recognized.
func parseSpanName(name string) (className, methodName string) {
	// Expected format: "ClassName.methodName" or "com.example.ClassName.methodName"
	idx := strings.LastIndex(name, ".")
	if idx < 0 {
		return "", ""
	}
	return name[:idx], name[idx+1:]
}
