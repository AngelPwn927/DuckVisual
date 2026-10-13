//go:build !windows

package main

import (
	"fmt"
	"os"
)

func runUI() {
	fmt.Println("The window UI is Windows-only. Use: ducklauncher -cli -name <Nickname>")
	os.Exit(runCLI("", "", "", false, "", ""))
}
