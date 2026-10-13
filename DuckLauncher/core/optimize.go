package core

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
)

// minor returns X of "1.X" / "1.X.Y" (0 if unknown).
func minor(version string) int {
	parts := strings.Split(version, ".")
	if len(parts) < 2 || parts[0] != "1" {
		return 0
	}
	n, _ := strconv.Atoi(parts[1])
	return n
}

// WeakPC is a machine where even the "Max FPS" profile struggles: 4 GB of RAM or less, or
// a dual-core CPU (old Pentium/Celeron laptops and office PCs).
func WeakPC(totalMemMB int) bool {
	return (totalMemMB > 0 && totalMemMB <= 4096) || runtime.NumCPU() <= 2
}

// ResolvePreset turns "auto" into a concrete graphics profile for this PC.
func ResolvePreset(preset string, totalMemMB int) string {
	if preset == "auto" {
		switch {
		case WeakPC(totalMemMB):
			return "potato"
		case totalMemMB > 0 && totalMemMB <= 6144:
			return "fps"
		}
		return "balanced"
	}
	return preset
}

// presetOptions are the options.txt values of a graphics profile.
func presetOptions(preset, version string) map[string]string {
	type p struct {
		rd, sim, gfx, ao, blend, clouds, particles, shadows, mip, scale, blur string
	}
	var v p
	switch preset {
	case "potato":
		v = p{"4", "5", "0", "false", "0", "false", "2", "false", "0", "0.5", "0"}
	case "fps":
		v = p{"6", "5", "0", "false", "0", "false", "2", "false", "0", "0.5", "0"}
	case "balanced":
		v = p{"10", "8", "1", "true", "2", "fast", "1", "true", "2", "1.0", "2"}
	case "quality":
		v = p{"16", "12", "1", "true", "5", "true", "0", "true", "4", "1.25", "5"}
	default:
		return nil
	}
	m := minor(version)
	o := map[string]string{
		"renderDistance":            v.rd,
		"graphicsMode":              v.gfx,
		"fancyGraphics":             strconv.FormatBool(v.gfx != "0"),
		"biomeBlendRadius":          v.blend,
		"particles":                 v.particles,
		"entityShadows":             v.shadows,
		"mipmapLevels":              v.mip,
		"maxFps":                    "260", // unlimited
		"enableVsync":               "false",
		"useVbo":                    "true", // 1.8-1.14
		"fboEnable":                 "true",
		"entityDistanceScaling":     v.scale,
		"menuBackgroundBlurriness":  v.blur, // 1.20.5+: the menu blur is expensive on weak GPUs
		"prioritizeChunkUpdates":    "0",
		"simulationDistance":        v.sim,
		"renderClouds":              v.clouds,
		"ao":                        v.ao,
	}
	if m >= 19 {
		o["renderClouds"] = `"` + v.clouds + `"`
	} else if v.ao == "true" {
		o["ao"] = "2" // before 1.19 smooth lighting was a number
	} else {
		o["ao"] = "0"
	}
	return o
}

// sodiumQuality tunes Sodium's own options for a profile, keeping everything else in the file.
func sodiumQuality(gameDir, preset string) {
	leaves, weather, vignette := "DEFAULT", "DEFAULT", true
	switch preset {
	case "fps", "potato":
		leaves, weather, vignette = "FAST", "FAST", false
	case "quality":
		leaves, weather = "FANCY", "FANCY"
	}
	path := filepath.Join(gameDir, "config", "sodium-options.json")
	cfg := map[string]any{}
	readJSON(path, &cfg)
	q, _ := cfg["quality"].(map[string]any)
	if q == nil {
		q = map[string]any{}
	}
	q["leaves_quality"], q["weather_quality"], q["enable_vignette"] = leaves, weather, vignette
	cfg["quality"] = q
	perf, _ := cfg["performance"].(map[string]any)
	if perf == nil {
		perf = map[string]any{}
	}
	for _, k := range []string{"always_defer_chunk_updates_v2", "animate_only_visible_textures", "use_entity_culling",
		"use_fog_occlusion", "use_block_face_culling", "use_no_error_g_l_context"} {
		perf[k] = true
	}
	perf["chunk_builder_threads"] = 0 // automatic
	cfg["performance"] = perf
	os.MkdirAll(filepath.Dir(path), 0o755)
	if b, err := json.MarshalIndent(cfg, "", "  "); err == nil {
		os.WriteFile(path, b, 0o644)
	}
}

// sodiumExtra turns off what a weak PC can't afford in the "potato" profile: animated textures
// (water, lava, fire are re-uploaded to the GPU every tick), rain splashes, block-crack particles,
// stars, tall beacon beams and tutorial toasts. Other profiles get the defaults back.
func sodiumExtra(gameDir, preset string) {
	on := preset != "potato"
	path := filepath.Join(gameDir, "config", "sodium-extra-options.json")
	cfg := map[string]any{}
	readJSON(path, &cfg)
	section := func(name string) map[string]any {
		m, _ := cfg[name].(map[string]any)
		if m == nil {
			m = map[string]any{}
			cfg[name] = m
		}
		return m
	}
	anim := section("animation_settings")
	for _, k := range []string{"animation", "water", "lava", "fire", "portal", "block_animations", "sculk_sensor"} {
		anim[k] = on
	}
	parts := section("particle_settings")
	parts["rain_splash"], parts["block_breaking"] = on, on
	section("detail_settings")["stars"] = on
	render := section("render_settings")
	render["limit_beacon_beam_height"], render["enchanting_table_book"] = !on, on
	extra := section("extra_settings")
	extra["tutorial_toast"], extra["recipe_toast"] = on, on
	os.MkdirAll(filepath.Dir(path), 0o755)
	if b, err := json.MarshalIndent(cfg, "", "  "); err == nil {
		os.WriteFile(path, b, 0o644)
	}
}

// duckVisualLight switches Duck Visual's real menu blur off on weak PCs (it re-renders the
// world through a blur shader every frame the menu is open). Only an existing config is touched,
// so the mod still applies its own first-launch defaults; returns false when there was nothing to change.
func duckVisualLight(gameDir, preset string) bool {
	path := filepath.Join(gameDir, "config", "duckvisual.json")
	cfg := map[string]any{}
	if !readJSON(path, &cfg) {
		return false
	}
	gui, _ := cfg["ClickGui"].(map[string]any)
	if gui == nil {
		return false
	}
	settings, _ := gui["settings"].(map[string]any)
	if settings == nil {
		settings = map[string]any{}
		gui["settings"] = settings
	}
	settings["Blur"] = preset != "potato"
	if menu, _ := cfg["MainMenu"].(map[string]any); menu != nil {
		if ms, _ := menu["settings"].(map[string]any); ms != nil {
			ms["Particles"] = preset != "potato"
		}
	}
	b, err := json.MarshalIndent(cfg, "", "  ")
	return err == nil && os.WriteFile(path, b, 0o644) == nil
}

// applyPreset writes a graphics profile into an instance once per change of the profile,
// so the player can still tweak single settings in the game afterwards.
func applyPreset(gameDir, version, preset string, sodium bool) {
	marker := filepath.Join(gameDir, ".duck-preset")
	if preset == "" || preset == "none" {
		os.Remove(marker)
		return
	}
	if b, err := os.ReadFile(marker); err == nil && strings.TrimSpace(string(b)) == preset {
		return
	}
	setOptions(filepath.Join(gameDir, "options.txt"), presetOptions(preset, version))
	if sodium {
		sodiumQuality(gameDir, preset)
		sodiumExtra(gameDir, preset)
	}
	os.WriteFile(marker, []byte(preset), 0o644)
}

// applyDuckPreset is applyPreset for Duck Visual's own config, with its own marker: the config
// only exists after the first game start, so this may happen one launch later.
func applyDuckPreset(gameDir, preset string) {
	marker := filepath.Join(gameDir, ".duck-preset-dv")
	if preset == "" || preset == "none" {
		return
	}
	if b, err := os.ReadFile(marker); err == nil && strings.TrimSpace(string(b)) == preset {
		return
	}
	if duckVisualLight(gameDir, preset) {
		os.WriteFile(marker, []byte(preset), 0o644)
	}
}

// jvmFlags are tuned G1 settings for a smooth client: short pauses, young-generation heavy
// (Minecraft allocates lots of short-lived objects every frame), no sync with perf counters.
// No -XX:+AutoCreateSharedArchive: with Minecraft's long classpath it made the JVM spend
// minutes in startup before the game even began loading (and Fabric's classes can't be
// archived anyway, so it never paid off).
func jvmFlags(memoryMB, totalMemMB int) []string {
	region := "8M"
	if memoryMB >= 4096 {
		region = "16M"
	}
	// on a 4 GB PC don't grab the whole heap at start: Windows itself needs the RAM
	initial := min(memoryMB, 2048)
	if totalMemMB > 0 && totalMemMB <= 4096 {
		initial = 512
	}
	f := []string{
		fmt.Sprintf("-Xmx%dm", memoryMB), fmt.Sprintf("-Xms%dm", initial),
		"-XX:+UseG1GC", "-XX:+UnlockExperimentalVMOptions", "-XX:+ParallelRefProcEnabled",
		"-XX:MaxGCPauseMillis=40", "-XX:+DisableExplicitGC",
		"-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=" + region,
		"-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
		"-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
		"-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32", "-XX:MaxTenuringThreshold=1",
		"-XX:+PerfDisableSharedMem",
		"-Djava.net.preferIPv4Stack=true", // faster server connect on networks with broken IPv6
		"-Dfile.encoding=UTF-8", "-Dduck.launcher=" + LauncherVersion,
	}
	if runtime.NumCPU() > 2 {
		// saves memory, but its background thread steals CPU time a dual-core can't spare
		f = append(f, "-XX:+UseStringDeduplication")
	}
	return f
}
