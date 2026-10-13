package core

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
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

// ResolvePreset turns "auto" into a concrete graphics profile for this PC.
func ResolvePreset(preset string, totalMemMB int) string {
	if preset == "auto" {
		if totalMemMB > 0 && totalMemMB <= 6144 {
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
	case "fps":
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
	}
	os.WriteFile(marker, []byte(preset), 0o644)
}

// jvmFlags are tuned G1 settings for a smooth client: short pauses, young-generation heavy
// (Minecraft allocates lots of short-lived objects every frame), no sync with perf counters.
func jvmFlags(memoryMB, javaMajor int, cacheDir string, fastStart bool) []string {
	region := "8M"
	if memoryMB >= 4096 {
		region = "16M"
	}
	f := []string{
		fmt.Sprintf("-Xmx%dm", memoryMB), fmt.Sprintf("-Xms%dm", min(memoryMB, 2048)),
		"-XX:+UseG1GC", "-XX:+UnlockExperimentalVMOptions", "-XX:+ParallelRefProcEnabled",
		"-XX:MaxGCPauseMillis=40", "-XX:+DisableExplicitGC",
		"-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=" + region,
		"-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
		"-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
		"-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32", "-XX:MaxTenuringThreshold=1",
		"-XX:+PerfDisableSharedMem", "-XX:+UseStringDeduplication",
		"-Djava.net.preferIPv4Stack=true", // faster server connect on networks with broken IPv6
		"-Dfile.encoding=UTF-8", "-Dduck.launcher=" + LauncherVersion,
	}
	if javaMajor >= 19 && fastStart && cacheDir != "" {
		// AppCDS: the JVM stores parsed classes after the first run; later starts are faster
		os.MkdirAll(cacheDir, 0o755)
		f = append(f, "-XX:+AutoCreateSharedArchive", "-XX:SharedArchiveFile="+filepath.Join(cacheDir, "classes.jsa"), "-Xlog:cds*=off")
	}
	return f
}
