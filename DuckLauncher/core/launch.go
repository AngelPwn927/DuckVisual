package core

import (
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// setOptions updates (or adds) keys in options.txt, keeping everything else the player set.
func setOptions(path string, values map[string]string) {
	b, _ := os.ReadFile(path)
	lines := strings.Split(strings.ReplaceAll(string(b), "\r\n", "\n"), "\n")
	done := map[string]bool{}
	for i, l := range lines {
		if k, _, ok := strings.Cut(l, ":"); ok {
			if v, want := values[k]; want {
				lines[i] = k + ":" + v
				done[k] = true
			}
		}
	}
	for k, v := range values {
		if !done[k] {
			lines = append(lines, k+":"+v)
		}
	}
	out := strings.TrimRight(strings.Join(lines, "\n"), "\n") + "\n"
	os.WriteFile(path, []byte(strings.TrimLeft(out, "\n")), 0o644)
}

// Prepare installs everything needed and returns a ready-to-start command.
func Prepare(cfg *Config, totalMemMB int, progress Progress) (*exec.Cmd, string, error) {
	if cfg.Account == nil {
		return nil, "", fmt.Errorf("сначала войди в аккаунт")
	}
	if _, err := loadManifest(); err != nil {
		return nil, "", fmt.Errorf("нет интернета: %w", err)
	}
	inst, err := Install(cfg.Version, cfg.Loader, progress)
	if err != nil {
		return nil, "", err
	}
	java, err := EnsureJava(inst.JavaMajor, progress)
	if err != nil {
		return nil, "", err
	}
	gameDir := InstanceDir(cfg.Version, cfg.Loader)
	if cfg.Loader == "fabric" {
		if err := EnsureMods(cfg.Version, gameDir, cfg.Mods, progress); err != nil {
			return nil, "", fmt.Errorf("моды: %w", err)
		}
	}
	var authlib string
	if cfg.Account.Type == "ely" {
		if err := ElyRefresh(cfg.Account); err != nil {
			return nil, "", fmt.Errorf("вход Ely.by устарел, войди заново: %w", err)
		}
		cfg.Save()
		if authlib, err = EnsureAuthlibInjector(progress); err != nil {
			return nil, "", err
		}
	}
	if progress != nil {
		progress("launch", 1, 1)
	}

	// options.txt: first launch preset, then language every time
	os.MkdirAll(gameDir, 0o755)
	opts := filepath.Join(gameDir, "options.txt")
	if !fileOK(Task{Path: opts}) {
		setOptions(opts, map[string]string{"onboardAccessibility": "false", "guiScale": "2", "skipMultiplayerWarning": "true"})
	}
	sodium := cfg.Loader == "fabric" && cfg.Mods["sodium"]
	applyPreset(gameDir, cfg.Version, ResolvePreset(cfg.Preset, totalMemMB), sodium)
	if cfg.Lang != "" {
		setOptions(opts, map[string]string{"lang": cfg.Lang})
	}
	EnsureServers(gameDir) // a broken servers.dat must not stop the game

	memory := cfg.MemoryMB
	if memory == 0 {
		memory = AutoMemoryMB(totalMemMB)
	}

	sep := string(os.PathListSeparator)
	userType := "legacy"
	if cfg.Account.Type == "ely" {
		userType = "mojang"
	}
	vars := map[string]string{
		"auth_player_name":    cfg.Account.Name,
		"version_name":        cfg.Version,
		"game_directory":      gameDir,
		"assets_root":         AssetsDir(),
		"game_assets":         AssetsDir(),
		"assets_index_name":   inst.Vanilla.AssetIndex.ID,
		"auth_uuid":           cfg.Account.UUID,
		"auth_access_token":   cfg.Account.AccessToken,
		"auth_session":        "token:" + cfg.Account.AccessToken + ":" + cfg.Account.UUID,
		"clientid":            "",
		"auth_xuid":           "",
		"user_type":           userType,
		"version_type":        "Duck Launcher",
		"natives_directory":   inst.Natives,
		"launcher_name":       LauncherName,
		"launcher_version":    LauncherVersion,
		"classpath":           strings.Join(inst.Classpath, sep),
		"classpath_separator": sep,
		"library_directory":   LibrariesDir(),
		"user_properties":     "{}",
		"resolution_width":    strconv.Itoa(cfg.Width),
		"resolution_height":   strconv.Itoa(cfg.Height),
	}
	sub := func(s string) string {
		for k, v := range vars {
			s = strings.ReplaceAll(s, "${"+k+"}", v)
		}
		return s
	}
	features := map[string]bool{"has_custom_resolution": !cfg.Fullscreen}
	quickPlay := false
	if cfg.QuickWorld != "" {
		features["is_quick_play_singleplayer"] = true
		vars["quickPlaySingleplayer"] = cfg.QuickWorld
	}
	if cfg.QuickJoin != "" {
		for _, a := range inst.Vanilla.Arguments.Game {
			for _, x := range a.Values {
				if strings.Contains(x, "${quickPlayMultiplayer}") {
					quickPlay = true
				}
			}
		}
		if quickPlay {
			features["is_quick_play_multiplayer"] = true
			vars["quickPlayMultiplayer"] = cfg.QuickJoin
		}
	}

	cds := filepath.Join(BaseDir(), "cache", "cds", fmt.Sprintf("%s-%s-j%d", cfg.Version, cfg.Loader, inst.JavaMajor))
	args := jvmFlags(memory, inst.JavaMajor, cds, cfg.FastStart)
	if authlib != "" {
		args = append(args, "-javaagent:"+authlib+"=ely.by")
	}
	if inst.LogConfig != "" {
		args = append(args, sub(strings.ReplaceAll(inst.Vanilla.Logging.Client.Argument, "${path}", inst.LogConfig)))
	}
	if extra := strings.TrimSpace(cfg.JVMArgs); extra != "" {
		args = append(args, strings.Fields(extra)...)
	}
	profiles := []*VersionJSON{inst.Vanilla}
	if inst.Fabric != nil {
		profiles = append(profiles, inst.Fabric)
	}
	if len(inst.Vanilla.Arguments.JVM) == 0 {
		// legacy versions describe no JVM arguments
		args = append(args, "-Djava.library.path="+inst.Natives, "-cp", vars["classpath"])
	}
	for _, v := range profiles {
		for _, a := range v.Arguments.JVM {
			if RulesAllowFeatures(a.Rules, features) {
				for _, x := range a.Values {
					args = append(args, sub(x))
				}
			}
		}
	}
	mainClass := inst.Vanilla.MainClass
	if inst.Fabric != nil {
		mainClass = inst.Fabric.MainClass
	}
	args = append(args, mainClass)
	if inst.Vanilla.MinecraftArguments != "" {
		for _, x := range strings.Fields(inst.Vanilla.MinecraftArguments) {
			args = append(args, sub(x))
		}
		if !cfg.Fullscreen {
			args = append(args, "--width", vars["resolution_width"], "--height", vars["resolution_height"])
		}
	}
	for _, v := range profiles {
		for _, a := range v.Arguments.Game {
			if RulesAllowFeatures(a.Rules, features) {
				for _, x := range a.Values {
					args = append(args, sub(x))
				}
			}
		}
	}
	if cfg.Fullscreen {
		args = append(args, "--fullscreen")
	}
	if cfg.QuickJoin != "" && !quickPlay {
		// versions before 1.20 join a server with --server/--port
		host, port, err := net.SplitHostPort(cfg.QuickJoin)
		if err != nil {
			host, port = cfg.QuickJoin, "25565"
		}
		args = append(args, "--server", host, "--port", port)
	}

	os.MkdirAll(LogsDir(), 0o755)
	cmd := exec.Command(java, args...)
	cmd.Dir = gameDir
	logPath := filepath.Join(LogsDir(), "game-"+time.Now().Format("2006-01-02_15-04-05")+".log")
	if f, err := os.Create(logPath); err == nil {
		cmd.Stdout = f
		cmd.Stderr = f
	}
	hideWindow(cmd, cfg.HighPrio)
	if cfg.DGPU {
		preferDiscreteGPU(java)
	}
	return cmd, logPath, nil
}
