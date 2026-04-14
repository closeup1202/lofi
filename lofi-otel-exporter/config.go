package lofiexporter

import "fmt"

// Config holds the configuration for the lofi exporter.
type Config struct {
	// BackendURL is the base URL of the lofi-backend server.
	// Example: "http://localhost:9292"
	BackendURL string `mapstructure:"backend_url"`
}

func (c *Config) Validate() error {
	if c.BackendURL == "" {
		return fmt.Errorf("backend_url must not be empty")
	}
	return nil
}
