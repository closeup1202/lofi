package lofiexporter

import "fmt"

// Config holds the configuration for the lofi exporter.
type Config struct {
	// BackendURL is the base URL of the lofi-backend server.
	// Example: "http://localhost:9292"
	BackendURL string `mapstructure:"backend_url"`

	// APIKey is sent as the X-Lofi-Api-Key header on every ingest request.
	// Required by lofi-backend >= 0.4.0.
	APIKey string `mapstructure:"api_key"`

	// ExcludePackages lists fully-qualified package patterns whose spans should be
	// dropped before ingestion. Matched against the className parsed from each
	// span name.
	//
	// Two forms are supported — semantics mirror the Actuator-mode
	// `lofi.exclude-packages` property:
	//   - "com.foo.*" — matches "com.foo" itself and any sub-package,
	//                   respecting package boundaries (so "com.foobar" is NOT matched).
	//   - "com.foo"   — legacy prefix match via HasPrefix.
	//   - "*"         — matches everything.
	//
	// Defaults to an empty list (no exclusions).
	ExcludePackages []string `mapstructure:"exclude_packages"`
}

func (c *Config) Validate() error {
	if c.BackendURL == "" {
		return fmt.Errorf("backend_url must not be empty")
	}
	if c.APIKey == "" {
		return fmt.Errorf("api_key must not be empty (required by lofi-backend >= 0.4.0)")
	}
	return nil
}
