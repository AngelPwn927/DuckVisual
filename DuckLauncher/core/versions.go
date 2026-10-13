package core

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// GameVersion is one entry of the version picker.
type GameVersion struct {
	ID     string `json:"id"`
	Fabric bool   `json:"fabric"` // Fabric Loader supports it
	Duck   bool   `json:"duck"`   // Duck Visual is available
	URL    string `json:"-"`
}

type manifest struct {
	Versions []struct {
		ID          string    `json:"id"`
		Type        string    `json:"type"`
		URL         string    `json:"url"`
		ReleaseTime time.Time `json:"releaseTime"`
	} `json:"versions"`
}

var oldest = time.Date(2015, 12, 1, 0, 0, 0, 0, time.UTC) // 1.8.9 and newer

func loadManifest() (*manifest, error) {
	cache := filepath.Join(VersionsDir(), "version_manifest_v2.json")
	var m manifest
	if st, err := os.Stat(cache); err == nil && time.Since(st.ModTime()) < 6*time.Hour {
		if b, err := os.ReadFile(cache); err == nil && json.Unmarshal(b, &m) == nil && len(m.Versions) > 0 {
			return &m, nil
		}
	}
	if err := getJSON("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", &m); err != nil {
		// offline: fall back to the cached copy even if old
		if b, rerr := os.ReadFile(cache); rerr == nil && json.Unmarshal(b, &m) == nil && len(m.Versions) > 0 {
			return &m, nil
		}
		return nil, err
	}
	os.MkdirAll(VersionsDir(), 0o755)
	if b, err := json.Marshal(m); err == nil {
		os.WriteFile(cache, b, 0o644)
	}
	return &m, nil
}

// ListVersions returns release versions (1.8.9+), newest first, marking Fabric support.
func ListVersions() ([]GameVersion, error) {
	m, err := loadManifest()
	if err != nil {
		return nil, err
	}
	fabric := map[string]bool{}
	var games []struct {
		Version string `json:"version"`
		Stable  bool   `json:"stable"`
	}
	if getJSON("https://meta.fabricmc.net/v2/versions/game", &games) == nil {
		for _, g := range games {
			fabric[g.Version] = true
		}
	}
	var out []GameVersion
	for _, v := range m.Versions {
		if v.Type != "release" || v.ReleaseTime.Before(oldest) {
			continue
		}
		if strings.HasPrefix(v.ID, "1.8") && v.ID != "1.8.9" {
			continue // only the PvP favourite of the 1.8 line
		}
		out = append(out, GameVersion{ID: v.ID, Fabric: fabric[v.ID], Duck: v.ID == DuckVersion, URL: v.URL})
	}
	return out, nil
}

func versionURL(id string) (string, error) {
	m, err := loadManifest()
	if err != nil {
		return "", err
	}
	for _, v := range m.Versions {
		if v.ID == id {
			return v.URL, nil
		}
	}
	return "", os.ErrNotExist
}
