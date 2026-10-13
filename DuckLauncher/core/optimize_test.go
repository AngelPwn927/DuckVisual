package core

import (
	"os"
	"path/filepath"
	"testing"
)

func TestDuckVisualLight(t *testing.T) {
	dir := t.TempDir()
	if duckVisualLight(dir, "potato") {
		t.Fatal("no config yet: nothing must be written, so the mod keeps its first-launch defaults")
	}
	path := filepath.Join(dir, "config", "duckvisual.json")
	os.MkdirAll(filepath.Dir(path), 0o755)
	os.WriteFile(path, []byte(`{"ClickGui":{"enabled":false,"key":344,"settings":{"Blur":true,"Descriptions":true}},"MainMenu":{"enabled":true,"key":-1,"settings":{"Particles":true}}}`), 0o644)
	if !duckVisualLight(dir, "potato") {
		t.Fatal("existing config must be updated")
	}
	var cfg map[string]map[string]any
	readJSON(path, &cfg)
	gui := cfg["ClickGui"]["settings"].(map[string]any)
	menu := cfg["MainMenu"]["settings"].(map[string]any)
	if gui["Blur"] != false || menu["Particles"] != false || gui["Descriptions"] != true || cfg["ClickGui"]["key"] != float64(344) {
		t.Fatalf("potato must switch off blur and menu particles only, got %v", cfg)
	}
	duckVisualLight(dir, "balanced")
	readJSON(path, &cfg)
	if cfg["ClickGui"]["settings"].(map[string]any)["Blur"] != true {
		t.Fatal("leaving potato must bring the blur back")
	}
}

func TestResolvePresetAuto(t *testing.T) {
	if got := ResolvePreset("auto", 4096); got != "potato" {
		t.Errorf("4 GB PC: got %q, want potato", got)
	}
	if got := ResolvePreset("fps", 4096); got != "fps" {
		t.Errorf("explicit choice must be kept, got %q", got)
	}
}
