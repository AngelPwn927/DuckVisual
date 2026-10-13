package main

import (
	_ "embed"
	"flag"
	"fmt"
	"os"
	"strings"

	"ducklauncher/core"
)

//go:embed embed/duck-visual.jar
var duckVisualJar []byte

//go:embed ui/index.html
var indexHTML string

//go:embed embed/hero.jpg
var heroJPG []byte

func main() {
	core.DuckVisualJar = duckVisualJar
	cli := flag.Bool("cli", false, "install and launch from the console")
	name := flag.String("name", "", "offline nickname (with -cli)")
	version := flag.String("version", "", "Minecraft version, e.g. 1.21.4 or 1.8.9 (with -cli)")
	loader := flag.String("loader", "", "fabric or vanilla (with -cli)")
	weak := flag.Bool("weak", false, "weak PC graphics preset for a new instance (with -cli)")
	join := flag.String("join", "", "server address to join right away (with -cli)")
	world := flag.String("world", "", "singleplayer world folder to open right away, 1.20+ (with -cli)")
	flag.Parse()
	if *cli {
		os.Exit(runCLI(*name, *version, *loader, *weak, *join, *world))
	}
	runUI()
}

func runCLI(name, version, loader string, weak bool, join, world string) int {
	cfg := core.LoadConfig()
	if name != "" {
		acc, err := core.OfflineAccount(name)
		if err != nil {
			fmt.Println("error:", err)
			return 1
		}
		cfg.Account = acc
	}
	if version != "" {
		cfg.Version = version
	}
	if loader != "" {
		cfg.Loader = loader
	}
	if weak {
		cfg.Preset = "fps"
	}
	cfg.Save()
	cfg.QuickJoin, cfg.QuickWorld = join, world
	last, lastPct := "", int64(-1)
	cmd, logPath, err := core.Prepare(cfg, totalMemoryMB(), func(stage string, done, total int64) {
		pct := int64(100)
		if total > 0 {
			pct = done * 100 / total
		}
		// one line per stage change and per 10%
		if stage != last || pct/10 != lastPct/10 {
			fmt.Printf("%-12s %3d%%  (%d/%d)\n", stage, pct, done, total)
			last, lastPct = stage, pct
		}
	})
	if err != nil {
		fmt.Println("error:", err)
		return 1
	}
	fmt.Println("launching:", strings.Join(cmd.Args[:4], " "), "...")
	fmt.Println("log:", logPath)
	if err := cmd.Start(); err != nil {
		fmt.Println("error:", err)
		return 1
	}
	if err := cmd.Wait(); err != nil {
		fmt.Println("game exited:", err)
		return 1
	}
	return 0
}
