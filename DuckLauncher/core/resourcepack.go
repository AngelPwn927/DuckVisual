package core

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
)

// PvPPack is the Duck PvP resource pack embedded into the launcher (set by main via go:embed).
var PvPPack []byte

const pvpPackFile = "Duck-PvP.zip"

// ApplyPvPPack puts the Duck PvP pack into the game and turns it on or off in options.txt.
// The pack is made for 1.21.4, so other versions are left alone.
func ApplyPvPPack(gameDir, version string, on bool) {
	if version != DuckVersion || len(PvPPack) == 0 {
		return
	}
	dir := filepath.Join(gameDir, "resourcepacks")
	path := filepath.Join(dir, pvpPackFile)
	if on {
		os.MkdirAll(dir, 0o755)
		if old, err := os.ReadFile(path); err != nil || !bytes.Equal(old, PvPPack) {
			os.WriteFile(path, PvPPack, 0o644)
		}
	}
	opts := filepath.Join(gameDir, "options.txt")
	b, _ := os.ReadFile(opts)
	cur := ""
	for _, l := range strings.Split(strings.ReplaceAll(string(b), "\r\n", "\n"), "\n") {
		if v, ok := strings.CutPrefix(l, "resourcePacks:"); ok {
			cur = v
		}
	}
	next := packList(cur, on)
	if next != cur {
		setOptions(opts, map[string]string{"resourcePacks": next})
	}
}

// packList adds or removes the pack in the options.txt list, e.g. ["vanilla","fabric","file/Duck-PvP.zip"].
// The pack goes last so it is on top of the others.
func packList(cur string, on bool) string {
	var items []string
	body := strings.TrimSpace(cur)
	body = strings.TrimSuffix(strings.TrimPrefix(body, "["), "]")
	for _, it := range strings.Split(body, ",") {
		it = strings.TrimSpace(it)
		if it == "" || it == `"file/`+pvpPackFile+`"` {
			continue
		}
		items = append(items, it)
	}
	if len(items) == 0 {
		if !on && cur == "" {
			return ""
		}
		items = []string{`"vanilla"`}
	}
	if on {
		items = append(items, `"file/`+pvpPackFile+`"`)
	}
	return "[" + strings.Join(items, ",") + "]"
}
