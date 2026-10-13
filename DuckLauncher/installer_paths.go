package main

import (
	"path/filepath"
	"strings"
)

// appFolder is the folder the launcher installs into inside the folder the player picks.
const appFolder = "Duck Launcher"

// installTarget turns the folder a player picked into the launcher folder: picking D:\Games
// installs into D:\Games\Duck Launcher, so uninstalling never touches the player's own files.
// It returns "" for anything that is not an absolute path.
func installTarget(picked string) string {
	d := strings.Trim(strings.TrimSpace(picked), `"`)
	if d == "" || !filepath.IsAbs(d) {
		return ""
	}
	d = filepath.Clean(d)
	if strings.EqualFold(filepath.Base(d), appFolder) {
		return d
	}
	return filepath.Join(d, appFolder)
}

// samePath compares paths the way Windows does: case-insensitively.
func samePath(a, b string) bool {
	return a != "" && b != "" && strings.EqualFold(filepath.Clean(a), filepath.Clean(b))
}
