//go:build windows

package main

import (
	"fmt"
	"runtime"
	"strings"
	"sync"

	"golang.org/x/sys/windows/registry"
)

// pcInfo is what the settings page shows under "Твой компьютер".
type pcInfo struct {
	CPU      string   `json:"cpu"`
	Cores    int      `json:"cores"`
	RAMMB    int      `json:"ramMb"`
	GPUs     []string `json:"gpus"`
	NoDriver bool     `json:"noDriver"` // only Windows' basic display adapter: the game will crawl
}

var (
	pcOnce   sync.Once
	pcCached pcInfo
)

func readPC() pcInfo {
	pcOnce.Do(func() {
		pcCached = pcInfo{Cores: runtime.NumCPU(), RAMMB: totalMemoryMB()}
		if k, err := registry.OpenKey(registry.LOCAL_MACHINE, `HARDWARE\DESCRIPTION\System\CentralProcessor\0`, registry.QUERY_VALUE); err == nil {
			if v, _, err := k.GetStringValue("ProcessorNameString"); err == nil {
				pcCached.CPU = strings.Join(strings.Fields(v), " ")
			}
			k.Close()
		}
		// display adapters: the device class of video cards
		const class = `SYSTEM\CurrentControlSet\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}`
		basicOnly := true
		seen := map[string]bool{}
		for i := 0; i < 16; i++ {
			k, err := registry.OpenKey(registry.LOCAL_MACHINE, fmt.Sprintf(`%s\%04d`, class, i), registry.QUERY_VALUE)
			if err != nil {
				continue
			}
			name, _, err := k.GetStringValue("DriverDesc")
			k.Close()
			if err != nil || name == "" || seen[name] {
				continue
			}
			seen[name] = true
			lower := strings.ToLower(name)
			if strings.Contains(lower, "remote") || strings.Contains(lower, "virtual") || strings.Contains(lower, "parsec") || strings.Contains(lower, "indirect") {
				continue // remote desktop / streaming adapters are not real GPUs
			}
			if !strings.Contains(lower, "basic display") && !strings.Contains(lower, "базовый") {
				basicOnly = false
			}
			pcCached.GPUs = append(pcCached.GPUs, name)
		}
		pcCached.NoDriver = len(pcCached.GPUs) > 0 && basicOnly
	})
	return pcCached
}
