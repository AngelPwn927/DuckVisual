//go:build windows

package core

import (
	"os/exec"
	"path/filepath"
	"syscall"

	"golang.org/x/sys/windows/registry"
)

// hideWindow makes sure no console window pops up for the game process and optionally
// raises its priority so background programs take CPU time from the game less often.
func hideWindow(cmd *exec.Cmd, highPriority bool) {
	flags := uint32(0x08000000) // CREATE_NO_WINDOW
	if highPriority {
		flags |= 0x00008000 // ABOVE_NORMAL_PRIORITY_CLASS
	}
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: flags}
}

// preferDiscreteGPU sets the per-app graphics preference (Settings → System → Display → Graphics)
// to "High performance", so laptops run the game on the discrete GPU instead of the integrated one.
func preferDiscreteGPU(java string) {
	k, _, err := registry.CreateKey(registry.CURRENT_USER, `Software\Microsoft\DirectX\UserGpuPreferences`, registry.SET_VALUE|registry.QUERY_VALUE)
	if err != nil {
		return
	}
	defer k.Close()
	dir := filepath.Dir(java)
	for _, exe := range []string{"java.exe", "javaw.exe"} {
		p := filepath.Join(dir, exe)
		if v, _, err := k.GetStringValue(p); err == nil && v != "" {
			continue // respect a choice the player made in Windows settings
		}
		k.SetStringValue(p, "GpuPreference=2;")
	}
}
