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
