//go:build windows

package main

import (
	_ "embed"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"syscall"
	"unsafe"

	"ducklauncher/core"

	ole "github.com/go-ole/go-ole"
	"github.com/go-ole/go-ole/oleutil"
	webview2 "github.com/jchv/go-webview2"
	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"
)

// The first start of a downloaded DuckLauncher.exe shows a small installer: it copies the
// launcher into a folder the player picks, creates shortcuts and registers an uninstaller in
// Windows Settings → Apps. Everything is per-user (HKCU, %LOCALAPPDATA%), so no admin rights
// are needed. Game files stay in %APPDATA%\.ducklauncher as before.

const (
	regKey     = `Software\DuckLauncher`
	uninstKey  = `Software\Microsoft\Windows\CurrentVersion\Uninstall\DuckLauncher`
	exeName    = "DuckLauncher.exe"
	linkName   = "Duck Launcher.lnk"
	publisher  = "Duck Visual"
	setupTitle = "Установка Duck Launcher"
)

//go:embed ui/setup.html
var setupHTML string

func selfExe() string {
	p, _ := os.Executable()
	if r, err := filepath.EvalSymlinks(p); err == nil {
		p = r
	}
	return p
}

func regGet(name string) string {
	k, err := registry.OpenKey(registry.CURRENT_USER, regKey, registry.QUERY_VALUE)
	if err != nil {
		return ""
	}
	defer k.Close()
	v, _, _ := k.GetStringValue(name)
	return v
}

func regSet(name, value string) {
	k, _, err := registry.CreateKey(registry.CURRENT_USER, regKey, registry.SET_VALUE)
	if err != nil {
		return
	}
	defer k.Close()
	k.SetStringValue(name, value)
}

// needSetup is true when this exe is not the installed copy (and the player did not choose
// to run this file without installing).
func needSetup() bool {
	exe := selfExe()
	if samePath(regGet("InstallDir"), filepath.Dir(exe)) || samePath(regGet("Portable"), exe) {
		return false
	}
	return true
}

func defaultInstallDir() string {
	if d := regGet("InstallDir"); d != "" {
		return d
	}
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		base = filepath.Join(os.Getenv("USERPROFILE"), "AppData", "Local")
	}
	return filepath.Join(base, "Programs", appFolder)
}

// cleanupOldExe removes the copy left behind when an update replaced a running launcher.
func cleanupOldExe() {
	os.Remove(selfExe() + ".old")
}

type setupInfo struct {
	Dir     string `json:"dir"`
	Update  bool   `json:"update"`
	Version string `json:"version"`
}

type setupOptions struct {
	Dir       string `json:"dir"`
	Desktop   bool   `json:"desktop"`
	StartMenu bool   `json:"startMenu"`
	Launch    bool   `json:"launch"`
}

// runSetup shows the installer. It returns true when the launcher should start right here
// (the player chose to run it without installing).
func runSetup() bool {
	w := webview2.NewWithOptions(webview2.WebViewOptions{
		AutoFocus: true,
		DataPath:  filepath.Join(os.TempDir(), "DuckLauncherSetup"),
		WindowOptions: webview2.WindowOptions{
			Title: setupTitle, Width: 560, Height: 580, IconId: 1, Center: true,
		},
	})
	if w == nil {
		return true // no WebView2: runUI explains how to get it
	}
	defer w.Destroy()
	w.SetSize(560, 580, webview2.HintFixed)
	runHere := false
	quit := func() { w.Dispatch(func() { w.Terminate() }) }

	w.Bind("dsInfo", func() setupInfo {
		installed := regGet("InstallDir")
		return setupInfo{Dir: defaultInstallDir(), Update: installed != "" && fileExists(filepath.Join(installed, exeName)), Version: core.LauncherVersion}
	})
	w.Bind("dsPickFolder", func(current string) string {
		picked := pickFolder(uintptr(w.Window()), "Куда установить Duck Launcher?", filepath.Dir(current))
		return installTarget(picked)
	})
	w.Bind("dsInstall", func(o setupOptions) string {
		dir := installTarget(o.Dir)
		if dir == "" {
			return "Укажи полный путь к папке, например C:\\Games"
		}
		exe, err := install(dir, o.Desktop, o.StartMenu)
		if err != nil {
			return err.Error()
		}
		if o.Launch {
			cmd := exec.Command(exe)
			cmd.Dir = dir
			if err := cmd.Start(); err != nil {
				return "Установлено, но не удалось запустить: " + err.Error()
			}
			quit()
		}
		return ""
	})
	w.Bind("dsRunHere", func() {
		regSet("Portable", selfExe())
		runHere = true
		quit()
	})
	w.Bind("dsClose", quit)
	w.Bind("dsOpenFolder", func(dir string) {
		if d := installTarget(dir); d != "" {
			exec.Command("explorer", d).Start()
		}
	})
	w.SetHtml(setupHTML)
	w.Run()
	return runHere
}

func fileExists(p string) bool {
	st, err := os.Stat(p)
	return err == nil && !st.IsDir()
}

// install copies this exe into dir, creates the chosen shortcuts and registers the uninstaller.
func install(dir string, desktop, startMenu bool) (string, error) {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", fmt.Errorf("Не удалось создать папку: %v", err)
	}
	target := filepath.Join(dir, exeName)
	self := selfExe()
	if !samePath(self, target) {
		data, err := os.ReadFile(self)
		if err != nil {
			return "", fmt.Errorf("Не удалось прочитать установщик: %v", err)
		}
		tmp := target + ".new"
		if err := os.WriteFile(tmp, data, 0o755); err != nil {
			if os.IsPermission(err) {
				return "", fmt.Errorf("Нет прав на запись в эту папку. Выбери другую, например в Документах или на диске D:")
			}
			return "", fmt.Errorf("Не удалось записать файл: %v", err)
		}
		// a running launcher can't be overwritten, but Windows lets us rename it out of the way
		if fileExists(target) {
			os.Remove(target + ".old")
			if err := os.Rename(target, target+".old"); err != nil {
				os.Remove(tmp)
				return "", fmt.Errorf("Закрой открытый Duck Launcher и попробуй ещё раз")
			}
		}
		if err := os.Rename(tmp, target); err != nil {
			os.Remove(tmp)
			return "", fmt.Errorf("Не удалось установить: %v", err)
		}
	}

	// a previous install in another folder: move its shortcuts and uninstaller here
	if old := regGet("InstallDir"); old != "" && !samePath(old, dir) {
		os.Remove(filepath.Join(old, exeName))
		os.Remove(filepath.Join(old, exeName+".old"))
		os.Remove(old) // only if empty
	}

	removeShortcuts()
	if desktop {
		if p, err := windows.KnownFolderPath(windows.FOLDERID_Desktop, 0); err == nil {
			makeShortcut(filepath.Join(p, linkName), target, dir)
		}
	}
	if startMenu {
		if p, err := windows.KnownFolderPath(windows.FOLDERID_Programs, 0); err == nil {
			makeShortcut(filepath.Join(p, linkName), target, dir)
		}
	}

	regSet("InstallDir", dir)
	if k, _, err := registry.CreateKey(registry.CURRENT_USER, regKey, registry.SET_VALUE); err == nil {
		k.DeleteValue("Portable")
		k.Close()
	}
	if k, _, err := registry.CreateKey(registry.CURRENT_USER, uninstKey, registry.SET_VALUE); err == nil {
		k.SetStringValue("DisplayName", "Duck Launcher")
		k.SetStringValue("DisplayVersion", core.LauncherVersion)
		k.SetStringValue("Publisher", publisher)
		k.SetStringValue("DisplayIcon", target+",0")
		k.SetStringValue("InstallLocation", dir)
		k.SetStringValue("UninstallString", `"`+target+`" --uninstall`)
		k.SetStringValue("URLInfoAbout", "https://duckvisual.ru")
		k.SetDWordValue("NoModify", 1)
		k.SetDWordValue("NoRepair", 1)
		if st, err := os.Stat(target); err == nil {
			k.SetDWordValue("EstimatedSize", uint32(st.Size()/1024))
		}
		k.Close()
	}
	return target, nil
}

func shortcutPaths() []string {
	var out []string
	for _, id := range []*windows.KNOWNFOLDERID{windows.FOLDERID_Desktop, windows.FOLDERID_Programs} {
		if p, err := windows.KnownFolderPath(id, 0); err == nil {
			out = append(out, filepath.Join(p, linkName))
		}
	}
	return out
}

func removeShortcuts() {
	for _, p := range shortcutPaths() {
		os.Remove(p)
	}
}

// makeShortcut writes a .lnk through the WScript.Shell COM object.
func makeShortcut(lnk, target, dir string) error {
	ole.CoInitializeEx(0, ole.COINIT_APARTMENTTHREADED) // already initialized on the UI thread: ignore
	unk, err := oleutil.CreateObject("WScript.Shell")
	if err != nil {
		return err
	}
	defer unk.Release()
	shell, err := unk.QueryInterface(ole.IID_IDispatch)
	if err != nil {
		return err
	}
	defer shell.Release()
	v, err := oleutil.CallMethod(shell, "CreateShortcut", lnk)
	if err != nil {
		return err
	}
	sc := v.ToIDispatch()
	defer sc.Release()
	oleutil.PutProperty(sc, "TargetPath", target)
	oleutil.PutProperty(sc, "WorkingDirectory", dir)
	oleutil.PutProperty(sc, "IconLocation", target+",0")
	oleutil.PutProperty(sc, "Description", "Duck Launcher — Minecraft с Duck Visual")
	_, err = oleutil.CallMethod(sc, "Save")
	return err
}

// ---------- folder picker (SHBrowseForFolder) ----------

type browseInfo struct {
	Owner       uintptr
	Root        uintptr
	DisplayName *uint16
	Title       *uint16
	Flags       uint32
	Callback    uintptr
	LParam      uintptr
	Image       int32
}

var browseCallback = syscall.NewCallback(func(hwnd, msg, lp, data uintptr) uintptr {
	const bffmInitialized, bffmSetSelectionW = 1, 0x400 + 103
	if msg == bffmInitialized && data != 0 {
		syscall.NewLazyDLL("user32.dll").NewProc("SendMessageW").Call(hwnd, bffmSetSelectionW, 1, data)
	}
	return 0
})

func pickFolder(owner uintptr, title, start string) string {
	shell32 := syscall.NewLazyDLL("shell32.dll")
	t, _ := syscall.UTF16PtrFromString(title)
	name := make([]uint16, windows.MAX_PATH)
	bi := browseInfo{Owner: owner, DisplayName: &name[0], Title: t, Flags: 0x0001 | 0x0040 | 0x0010, Callback: browseCallback} // only folders, new style, edit box
	var startPtr *uint16
	for start != "" && !fileExistsDir(start) {
		parent := filepath.Dir(start)
		if parent == start {
			start = ""
			break
		}
		start = parent
	}
	if start != "" {
		startPtr, _ = syscall.UTF16PtrFromString(start)
		bi.LParam = uintptr(unsafe.Pointer(startPtr))
	}
	pidl, _, _ := shell32.NewProc("SHBrowseForFolderW").Call(uintptr(unsafe.Pointer(&bi)))
	if pidl == 0 {
		return ""
	}
	defer syscall.NewLazyDLL("ole32.dll").NewProc("CoTaskMemFree").Call(pidl)
	buf := make([]uint16, 32768)
	r, _, _ := shell32.NewProc("SHGetPathFromIDListEx").Call(pidl, uintptr(unsafe.Pointer(&buf[0])), uintptr(len(buf)), 0)
	if r == 0 {
		return ""
	}
	runtime.KeepAlive(startPtr) // the dialog read the start path through LParam
	return windows.UTF16ToString(buf)
}

func fileExistsDir(p string) bool {
	st, err := os.Stat(p)
	return err == nil && st.IsDir()
}

// ---------- uninstall (Settings → Apps → Duck Launcher → Uninstall) ----------

func uninstallApp() {
	if !askYesNo("Удаление Duck Launcher", "Удалить Duck Launcher с компьютера?", false) {
		return
	}
	data := core.BaseDir()
	if fileExistsDir(data) && askYesNo("Удаление Duck Launcher",
		"Удалить также игру, миры, скриншоты и настройки?\n\n"+data+"\n\nНажми «Нет», если хочешь сохранить миры.", true) {
		os.RemoveAll(data)
	}
	removeShortcuts()
	dir := regGet("InstallDir")
	registry.DeleteKey(registry.CURRENT_USER, uninstKey)
	registry.DeleteKey(registry.CURRENT_USER, regKey)
	os.RemoveAll(filepath.Join(os.TempDir(), "DuckLauncherSetup"))

	self := selfExe()
	if dir == "" {
		dir = filepath.Dir(self)
	}
	target := filepath.Join(dir, exeName)
	os.Remove(target + ".old")
	if samePath(self, target) {
		// a running exe can't delete itself: a hidden cmd removes it a moment after we exit,
		// then removes the folder only if nothing else is in it
		cmd := exec.Command("cmd.exe")
		cmd.SysProcAttr = &syscall.SysProcAttr{
			HideWindow:    true,
			CreationFlags: 0x08000000, // CREATE_NO_WINDOW
			CmdLine:       `cmd.exe /c ping 127.0.0.1 -n 3 >nul & del /f /q "` + target + `" & rmdir "` + dir + `"`,
		}
		cmd.Start()
	} else {
		os.Remove(target)
		os.Remove(dir)
	}
	messageBox("Duck Launcher", "Duck Launcher удалён.")
}

func askYesNo(title, text string, defaultNo bool) bool {
	t, _ := syscall.UTF16PtrFromString(title)
	m, _ := syscall.UTF16PtrFromString(text)
	flags := uintptr(0x04 | 0x20) // MB_YESNO | MB_ICONQUESTION
	if defaultNo {
		flags |= 0x100 // MB_DEFBUTTON2
	}
	r, _, _ := syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW").Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), flags)
	return r == 6 // IDYES
}
