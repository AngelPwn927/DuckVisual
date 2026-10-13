package core

import (
	"encoding/json"
	"os"
	"path/filepath"
)

type Config struct {
	Account *Account `json:"account"`

	Version string `json:"version"` // Minecraft version id
	Loader  string `json:"loader"`  // "fabric" or "vanilla"

	MemoryMB    int             `json:"memoryMb"` // 0 = automatic
	Mods        map[string]bool `json:"mods"`     // performance mods by Modrinth slug
	WeakPC      bool            `json:"weakPc"`   // legacy: light graphics preset (now Preset "fps")
	Preset      string          `json:"preset"`   // graphics profile: none, auto, fps, balanced, quality
	HighPrio    bool            `json:"highPriority"`
	DGPU        bool            `json:"dgpu"`     // prefer the discrete GPU on laptops
	FastStart   bool            `json:"fastStart"` // Java class data sharing cache
	ModsRev     int             `json:"modsRev"`  // bumps when new performance mods are added
	Width       int             `json:"width"`
	Height      int             `json:"height"`
	Fullscreen  bool            `json:"fullscreen"`
	Lang        string          `json:"lang"`
	AfterLaunch string          `json:"afterLaunch"` // "keep", "minimize", "close"
	JVMArgs     string          `json:"jvmArgs"`
	CFKey       string          `json:"cfKey"` // CurseForge API key for the mods catalog

	// QuickJoin is a server address to connect to right after start (not saved).
	QuickJoin string `json:"-"`
	// QuickWorld is a singleplayer world to open right after start (1.20+, not saved).
	QuickWorld string `json:"-"`

	// legacy fields from launcher 1.0
	Sodium     *bool `json:"sodium,omitempty"`
	CloseOnRun *bool `json:"closeOnRun,omitempty"`
}

func configPath() string { return filepath.Join(BaseDir(), "launcher.json") }

func LoadConfig() *Config {
	c := &Config{}
	if b, err := os.ReadFile(configPath()); err == nil {
		json.Unmarshal(b, c)
	}
	if c.Version == "" {
		c.Version = DuckVersion
	}
	if c.Loader == "" {
		c.Loader = "fabric"
	}
	fresh := c.Mods == nil
	if fresh {
		c.Mods = DefaultMods()
		if c.Sodium != nil && !*c.Sodium {
			c.Mods["sodium"] = false
		}
	}
	if c.ModsRev < 2 {
		// launcher 1.3 added many performance mods: switch the new ones on for existing players too
		for slug, on := range DefaultMods() {
			if _, known := c.Mods[slug]; !known {
				c.Mods[slug] = on
			}
		}
		if fresh || c.Preset == "" {
			c.HighPrio, c.DGPU, c.FastStart = true, true, true
		}
		c.ModsRev = 2
	}
	if c.Preset == "" {
		c.Preset = "none" // players updating from 1.2 keep their own graphics settings
		if fresh {
			c.Preset = "auto"
		}
		if c.WeakPC {
			c.Preset = "fps"
		}
	}
	if c.AfterLaunch == "" {
		c.AfterLaunch = "minimize"
		if c.CloseOnRun != nil && *c.CloseOnRun {
			c.AfterLaunch = "close"
		}
	}
	c.Sodium, c.CloseOnRun = nil, nil
	if c.Lang == "" {
		c.Lang = "ru_ru"
	}
	if c.Width == 0 || c.Height == 0 {
		c.Width, c.Height = 1280, 720
	}
	if c.MemoryMB != 0 && c.MemoryMB < 1024 {
		c.MemoryMB = 1024
	}
	return c
}

// AutoMemoryMB picks a sensible heap for the machine's RAM (weak PCs get less so Windows does not swap).
func AutoMemoryMB(totalMB int) int {
	switch {
	case totalMB <= 4096:
		return 1536
	case totalMB <= 8192:
		return 2560
	case totalMB <= 16384:
		return 4096
	default:
		return 6144
	}
}

func (c *Config) Save() error {
	os.MkdirAll(BaseDir(), 0o755)
	b, _ := json.MarshalIndent(c, "", "  ")
	return os.WriteFile(configPath(), b, 0o600)
}
