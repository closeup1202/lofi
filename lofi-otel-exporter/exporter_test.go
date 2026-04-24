package lofiexporter

import "testing"

func TestPackageMatcher(t *testing.T) {
	tests := []struct {
		name      string
		patterns  []string
		className string
		want      bool
	}{
		{
			name:      "empty patterns never match",
			patterns:  nil,
			className: "com.acme.Foo",
			want:      false,
		},
		{
			name:      "bare prefix matches via HasPrefix",
			patterns:  []string{"com.acme.ops"},
			className: "com.acme.ops.FooController",
			want:      true,
		},
		{
			name:      "bare prefix can over-match (documented legacy behavior)",
			patterns:  []string{"com.acme.ops"},
			className: "com.acme.ops2.FooController", // unintended match, same as Java side
			want:      true,
		},
		{
			name:      "wildcard respects package boundaries: exact base matches",
			patterns:  []string{"com.acme.ops.*"},
			className: "com.acme.ops",
			want:      true,
		},
		{
			name:      "wildcard respects package boundaries: sub-package matches",
			patterns:  []string{"com.acme.ops.*"},
			className: "com.acme.ops.FooController",
			want:      true,
		},
		{
			name:      "wildcard respects package boundaries: sibling does NOT match",
			patterns:  []string{"com.acme.ops.*"},
			className: "com.acme.ops2.FooController",
			want:      false,
		},
		{
			name:      "bare '*' matches everything",
			patterns:  []string{"*"},
			className: "anything.goes.Here",
			want:      true,
		},
		{
			name:      "multiple patterns — any match wins",
			patterns:  []string{"com.other.*", "com.acme.ops.*"},
			className: "com.acme.ops.FooController",
			want:      true,
		},
		{
			name:      "whitespace in pattern is trimmed",
			patterns:  []string{"  com.acme.ops.*  "},
			className: "com.acme.ops.FooController",
			want:      true,
		},
		{
			name:      "empty/blank entries are skipped, not treated as match-all",
			patterns:  []string{"", "   "},
			className: "com.acme.ops.FooController",
			want:      false,
		},
		{
			name:      "lone '.*' base is empty and gets dropped",
			patterns:  []string{".*"},
			className: "com.acme.ops.FooController",
			want:      false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			matchers := compileMatchers(tt.patterns)
			if got := isExcluded(matchers, tt.className); got != tt.want {
				t.Fatalf("isExcluded(%q, %q) = %v, want %v", tt.patterns, tt.className, got, tt.want)
			}
		})
	}
}
