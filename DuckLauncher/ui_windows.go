//go:build windows

package main

import (
	"encoding/base64"
	"errors"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"unsafe"

	"ducklauncher/core"

	webview2 "github.com/jchv/go-webview2"
)

type app struct {
	w       webview2.WebView
	cfg     *core.Config
	mu      sync.Mutex
	running bool
	busy    bool
}

func messageBox(title, text string) {
	t, _ := syscall.UTF16PtrFromString(title)
	m, _ := syscall.UTF16PtrFromString(text)
	syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW").Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), 0x40)
}

// totalMemoryMB uses GlobalMemoryStatusEx to size the RAM slider.
func totalMemoryMB() int {
	type memStatus struct {
		Length               uint32
		MemoryLoad           uint32
		TotalPhys            uint64
		AvailPhys            uint64
		TotalPageFile        uint64
		AvailPageFile        uint64
		TotalVirtual         uint64
		AvailVirtual         uint64
		AvailExtendedVirtual uint64
	}
	var m memStatus
	m.Length = uint32(unsafe.Sizeof(m))
	r, _, _ := syscall.NewLazyDLL("kernel32.dll").NewProc("GlobalMemoryStatusEx").Call(uintptr(unsafe.Pointer(&m)))
	if r == 0 {
		return 8192
	}
	return int(m.TotalPhys / 1024 / 1024)
}

func runUI() {
	a := &app{cfg: core.LoadConfig()}
	os.MkdirAll(core.BaseDir(), 0o755)
	w := webview2.NewWithOptions(webview2.WebViewOptions{
		Debug:     false,
		AutoFocus: true,
		DataPath:  filepath.Join(core.BaseDir(), "webview"),
		WindowOptions: webview2.WindowOptions{
			Title:  "Duck Launcher",
			Width:  1000,
			Height: 640,
			IconId: 1,
			Center: true,
		},
	})
	if w == nil {
		messageBox("Duck Launcher", "Для окна лаунчера нужен Microsoft Edge WebView2 Runtime.\n\n"+
			"Он уже есть в Windows 11. Если у тебя Windows 10 — установи его с сайта Microsoft "+
			"(«WebView2 Runtime»), откроется страница загрузки.")
		exec.Command("rundll32", "url.dll,FileProtocolHandler", "https://developer.microsoft.com/microsoft-edge/webview2/").Start()
		return
	}
	a.w = w
	defer w.Destroy()
	w.SetSize(1000, 640, webview2.HintFixed)

	w.Bind("dlState", a.state)
	w.Bind("dlLoginOffline", a.loginOffline)
	w.Bind("dlLoginEly", a.loginEly)
	w.Bind("dlLogout", a.logout)
	w.Bind("dlSaveSettings", a.saveSettings)
	w.Bind("dlVersions", a.versions)
	w.Bind("dlSetVersion", a.setVersion)
	w.Bind("dlOpenURL", a.openURL)
	w.Bind("dlPlay", a.play)
	w.Bind("dlOpen", a.open)
	w.Bind("dlServers", a.servers)
	w.Bind("dlPingServer", a.pingServer)
	w.Bind("dlJoinServer", a.joinServer)
	w.Bind("dlModsSearch", a.modsSearch)
	w.Bind("dlModsInstalled", a.modsInstalled)
	w.Bind("dlModInstall", a.modInstall)
	w.Bind("dlModRemove", a.modRemove)
	w.SetHtml(strings.Replace(indexHTML, "{{HERO}}", base64.StdEncoding.EncodeToString(heroJPG), 1))
	w.Run()
}

func (a *app) emit(fn string, args ...any) {
	b, _ := json.Marshal(args)
	js := "window." + fn + " && window." + fn + ".apply(null, " + string(b) + ")"
	a.w.Dispatch(func() { a.w.Eval(js) })
}

type state struct {
	Account     *core.Account      `json:"account"`
	Version     string             `json:"version"`
	Loader      string             `json:"loader"`
	DuckVersion string             `json:"duckVersion"`
	MemoryMB    int                `json:"memoryMb"`
	AutoMemory  int                `json:"autoMemoryMb"`
	MaxMemoryMB int                `json:"maxMemoryMb"`
	Mods        map[string]bool    `json:"mods"`
	PerfMods    []core.OptionalMod `json:"perfMods"`
	Preset      string             `json:"preset"`
	HighPrio    bool               `json:"highPriority"`
	DGPU        bool               `json:"dgpu"`
	FastStart   bool               `json:"fastStart"`
	Width       int                `json:"width"`
	Height      int                `json:"height"`
	Fullscreen  bool               `json:"fullscreen"`
	Lang        string             `json:"lang"`
	AfterLaunch string             `json:"afterLaunch"`
	JVMArgs     string             `json:"jvmArgs"`
	CFKey       bool               `json:"cfKey"`
	Running     bool               `json:"running"`
	Busy        bool               `json:"busy"`
	AppVersion  string             `json:"appVersion"`
	Dir         string             `json:"dir"`
}

func (a *app) state() state {
	a.mu.Lock()
	defer a.mu.Unlock()
	c := a.cfg
	var safe *core.Account
	if c.Account != nil {
		safe = &core.Account{Type: c.Account.Type, Name: c.Account.Name}
	}
	total := totalMemoryMB()
	return state{Account: safe, Version: c.Version, Loader: c.Loader, DuckVersion: core.DuckVersion,
		MemoryMB: c.MemoryMB, AutoMemory: core.AutoMemoryMB(total), MaxMemoryMB: total, Mods: c.Mods, PerfMods: core.PerfMods,
		Preset: c.Preset, HighPrio: c.HighPrio, DGPU: c.DGPU, FastStart: c.FastStart, Width: c.Width, Height: c.Height, Fullscreen: c.Fullscreen, Lang: c.Lang,
		AfterLaunch: c.AfterLaunch, JVMArgs: c.JVMArgs, CFKey: c.CFKey != "", Running: a.running, Busy: a.busy,
		AppVersion: core.LauncherVersion, Dir: core.BaseDir()}
}

func (a *app) versions() ([]core.GameVersion, error) {
	return core.ListVersions()
}

func (a *app) setVersion(version, loader string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if version != "" {
		a.cfg.Version = version
	}
	if loader == "fabric" || loader == "vanilla" {
		a.cfg.Loader = loader
	}
	a.cfg.Save()
}

func (a *app) loginOffline(name string) string {
	acc, err := core.OfflineAccount(name)
	if err != nil {
		return err.Error()
	}
	a.mu.Lock()
	a.cfg.Account = acc
	a.cfg.Save()
	a.mu.Unlock()
	return ""
}

func (a *app) loginEly(login, password string) string {
	acc, err := core.ElyLogin(login, password)
	if err != nil {
		return err.Error()
	}
	a.mu.Lock()
	a.cfg.Account = acc
	a.cfg.Save()
	a.mu.Unlock()
	return ""
}

func (a *app) logout() {
	a.mu.Lock()
	a.cfg.Account = nil
	a.cfg.Save()
	a.mu.Unlock()
}

// settings is what the settings page sends back.
type settings struct {
	MemoryMB    int             `json:"memoryMb"`
	Mods        map[string]bool `json:"mods"`
	Preset      string          `json:"preset"`
	HighPrio    bool            `json:"highPriority"`
	DGPU        bool            `json:"dgpu"`
	FastStart   bool            `json:"fastStart"`
	Width       int             `json:"width"`
	Height      int             `json:"height"`
	Fullscreen  bool            `json:"fullscreen"`
	Lang        string          `json:"lang"`
	AfterLaunch string          `json:"afterLaunch"`
	JVMArgs     string          `json:"jvmArgs"`
	CFKey       *string         `json:"cfKey"` // nil = unchanged
}

func (a *app) saveSettings(s settings) {
	a.mu.Lock()
	defer a.mu.Unlock()
	c := a.cfg
	if s.MemoryMB == 0 || s.MemoryMB >= 1024 {
		c.MemoryMB = s.MemoryMB
	}
	if s.Mods != nil {
		c.Mods = s.Mods
	}
	switch s.Preset {
	case "none", "auto", "fps", "balanced", "quality":
		c.Preset = s.Preset
	}
	c.WeakPC = false
	c.HighPrio, c.DGPU, c.FastStart = s.HighPrio, s.DGPU, s.FastStart
	if s.Width >= 320 && s.Height >= 240 {
		c.Width, c.Height = s.Width, s.Height
	}
	c.Fullscreen = s.Fullscreen
	if s.Lang != "" {
		c.Lang = s.Lang
	}
	if s.AfterLaunch == "keep" || s.AfterLaunch == "minimize" || s.AfterLaunch == "close" {
		c.AfterLaunch = s.AfterLaunch
	}
	c.JVMArgs = s.JVMArgs
	if s.CFKey != nil {
		c.CFKey = strings.TrimSpace(*s.CFKey)
	}
	c.Save()
}

func (a *app) open(kind string) {
	a.mu.Lock()
	game := core.InstanceDir(a.cfg.Version, a.cfg.Loader)
	a.mu.Unlock()
	dir := game
	switch kind {
	case "mods":
		dir = filepath.Join(game, "mods")
	case "logs":
		dir = core.LogsDir()
	case "screenshots":
		dir = filepath.Join(game, "screenshots")
	case "base":
		dir = core.BaseDir()
	}
	os.MkdirAll(dir, 0o755)
	exec.Command("explorer", dir).Start()
}

// openURL only opens the links the UI actually shows.
func (a *app) openURL(u string) {
	allowed := []string{"https://t.me/", "https://ely.by", "https://modrinth.com/", "https://www.curseforge.com/", "https://console.curseforge.com/"}
	ok := false
	for _, p := range allowed {
		ok = ok || strings.HasPrefix(u, p)
	}
	if ok {
		exec.Command("rundll32", "url.dll,FileProtocolHandler", u).Start()
	}
}

var procShowWindow = syscall.NewLazyDLL("user32.dll").NewProc("ShowWindow")

func (a *app) showWindow(cmd uintptr) {
	a.w.Dispatch(func() { procShowWindow.Call(uintptr(a.w.Window()), cmd) })
}

func (a *app) play() string { return a.launch("") }

func (a *app) servers() []core.Server { return core.Servers }

func (a *app) pingServer(i int) core.ServerStatus {
	if i < 0 || i >= len(core.Servers) {
		return core.ServerStatus{Error: "нет сервера"}
	}
	return core.PingServer(core.Servers[i])
}

func (a *app) joinServer(i int) string {
	if i < 0 || i >= len(core.Servers) {
		return "нет сервера"
	}
	return a.launch(core.Servers[i].Address())
}

// modTarget is the version and folder the "Моды" tab works with.
func (a *app) modTarget() (version, dir, key string, perf map[string]bool, err string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.cfg.Loader != "fabric" {
		return "", "", "", nil, "Моды работают только на Fabric — выбери Fabric в меню версий"
	}
	perf = map[string]bool{}
	for k, v := range a.cfg.Mods {
		perf[k] = v
	}
	return a.cfg.Version, core.InstanceDir(a.cfg.Version, a.cfg.Loader), a.cfg.CFKey, perf, ""
}

func (a *app) modsSearch(source, query, sort string, page int) ([]core.CatalogMod, error) {
	v, dir, key, perf, e := a.modTarget()
	if e != "" {
		return nil, errors.New(e)
	}
	return core.SearchMods(source, query, v, sort, key, page, dir, perf)
}

func (a *app) modsInstalled() []core.UserMod {
	_, dir, _, _, e := a.modTarget()
	if e != "" {
		return []core.UserMod{}
	}
	return core.InstalledMods(dir)
}

// modInstall runs in the background and reports through onModInstalled(id, title, error).
func (a *app) modInstall(source, id string) {
	v, dir, key, perf, e := a.modTarget()
	if e != "" {
		a.emit("onModInstalled", id, "", e)
		return
	}
	go func() {
		title, err := core.InstallMod(source, id, v, key, dir, perf, func(stage string, done, total int64) {
			a.emit("onModProgress", id, done, total)
		})
		msg := ""
		if err != nil {
			msg = err.Error()
		}
		a.emit("onModInstalled", id, title, msg)
	}()
}

func (a *app) modRemove(source, id string) string {
	_, dir, _, _, e := a.modTarget()
	if e != "" {
		return e
	}
	if err := core.RemoveMod(dir, source, id); err != nil {
		return err.Error()
	}
	return ""
}

func (a *app) launch(join string) string {
	a.mu.Lock()
	if a.busy || a.running {
		a.mu.Unlock()
		return "Игра уже запускается"
	}
	if a.cfg.Account == nil {
		a.mu.Unlock()
		return "Сначала войди в аккаунт"
	}
	a.busy = true
	a.mu.Unlock()

	go func() {
		a.mu.Lock()
		a.cfg.QuickJoin = join
		a.mu.Unlock()
		cmd, logPath, err := core.Prepare(a.cfg, totalMemoryMB(), func(stage string, done, total int64) {
			a.emit("onProgress", stage, done, total)
		})
		if err == nil {
			err = cmd.Start()
		}
		a.mu.Lock()
		a.busy = false
		a.mu.Unlock()
		if err != nil {
			a.emit("onError", err.Error())
			return
		}
		a.mu.Lock()
		a.running = true
		after := a.cfg.AfterLaunch
		a.mu.Unlock()
		a.emit("onLaunched", logPath)
		switch after {
		case "close":
			// the game keeps running on its own; the launcher frees its memory
			a.w.Dispatch(func() { a.w.Terminate() })
			return
		case "minimize":
			a.showWindow(6) // SW_MINIMIZE
		}
		werr := cmd.Wait()
		a.mu.Lock()
		a.running = false
		a.mu.Unlock()
		if after == "minimize" {
			a.showWindow(9) // SW_RESTORE
		}
		msg := ""
		if werr != nil {
			msg = werr.Error()
		}
		a.emit("onGameExit", msg, logPath)
	}()
	return ""
}
