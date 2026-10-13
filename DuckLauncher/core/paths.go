package core

import (
	"os"
	"path/filepath"
	"runtime"
)

const (
	// DuckVersion is the Minecraft version Duck Visual is built for.
	DuckVersion     = "1.21.4"
	LauncherName    = "DuckLauncher"
	LauncherVersion = "1.3.0"
)

// BaseDir is where everything lives: %APPDATA%\.ducklauncher on Windows, ~/.ducklauncher elsewhere.
func BaseDir() string {
	if runtime.GOOS == "windows" {
		if appData := os.Getenv("APPDATA"); appData != "" {
			return filepath.Join(appData, ".ducklauncher")
		}
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".ducklauncher")
}

// InstanceDir is the game directory for one version+loader. The Duck Visual instance keeps
// the original "minecraft" folder so existing worlds/configs stay where they were.
func InstanceDir(version, loader string) string {
	if version == DuckVersion && loader == "fabric" {
		return filepath.Join(BaseDir(), "minecraft")
	}
	return filepath.Join(BaseDir(), "instances", version+"-"+loader)
}

func GameDir() string      { return InstanceDir(DuckVersion, "fabric") }
func LibrariesDir() string { return filepath.Join(BaseDir(), "libraries") }
func AssetsDir() string    { return filepath.Join(BaseDir(), "assets") }
func VersionsDir() string  { return filepath.Join(BaseDir(), "versions") }
func RuntimeDir() string   { return filepath.Join(BaseDir(), "runtime") }
func ModsDir() string      { return filepath.Join(GameDir(), "mods") }
func LogsDir() string      { return filepath.Join(BaseDir(), "logs") }
