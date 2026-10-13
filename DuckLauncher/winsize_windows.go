//go:build windows

package main

import (
	"strconv"
	"unsafe"

	"github.com/jchv/go-webview2"
	"golang.org/x/sys/windows"
)

var (
	user32             = windows.NewLazySystemDLL("user32.dll")
	procGetDpiForWin   = user32.NewProc("GetDpiForWindow")
	procMonitorFromWin = user32.NewProc("MonitorFromWindow")
	procGetMonitorInfo = user32.NewProc("GetMonitorInfoW")
	procGetWindowRect  = user32.NewProc("GetWindowRect")
	procGetClientRect  = user32.NewProc("GetClientRect")
	procSetWindowPos   = user32.NewProc("SetWindowPos")
)

type rect struct{ Left, Top, Right, Bottom int32 }

type monitorInfo struct {
	Size    uint32
	Monitor rect
	Work    rect
	Flags   uint32
}

// fitWindow gives a fixed-size window the page size it was designed for (in CSS pixels) on any
// Windows display scale (125%, 150%...), keeps it inside the screen and centers it. The app is
// per-monitor DPI aware, so sizes must be in physical pixels: without this the page is cut off.
// If the screen is too small even so, the page is zoomed out to fit. Call it before SetHtml.
func fitWindow(w webview2.WebView, width, height int) {
	hwnd := uintptr(w.Window())
	scale := 1.0
	if procGetDpiForWin.Find() == nil {
		if dpi, _, _ := procGetDpiForWin.Call(hwnd); dpi >= 96 {
			scale = float64(dpi) / 96
		}
	}
	w.SetSize(int(float64(width)*scale), int(float64(height)*scale), webview2.HintFixed)

	// frame (title bar, borders) as Windows really draws it at this DPI
	var wr, cr rect
	procGetWindowRect.Call(hwnd, uintptr(unsafe.Pointer(&wr)))
	procGetClientRect.Call(hwnd, uintptr(unsafe.Pointer(&cr)))
	frameW := int(wr.Right-wr.Left) - int(cr.Right)
	frameH := int(wr.Bottom-wr.Top) - int(cr.Bottom)

	mi := monitorInfo{Size: uint32(unsafe.Sizeof(monitorInfo{}))}
	mon, _, _ := procMonitorFromWin.Call(hwnd, 2 /* MONITOR_DEFAULTTONEAREST */)
	procGetMonitorInfo.Call(mon, uintptr(unsafe.Pointer(&mi)))
	workW, workH := int(mi.Work.Right-mi.Work.Left), int(mi.Work.Bottom-mi.Work.Top)

	clientW, clientH := float64(width)*scale, float64(height)*scale
	fit := 1.0
	if workW > 0 && workH > 0 {
		fit = min(1, float64(workW-frameW)/clientW, float64(workH-frameH)/clientH)
	}
	cw, ch := int(clientW*fit), int(clientH*fit)
	ww, wh := cw+frameW, ch+frameH
	x, y := int(mi.Work.Left)+(workW-ww)/2, int(mi.Work.Top)+(workH-wh)/2
	if workW <= 0 {
		x, y = int(wr.Left), int(wr.Top)
	}
	const swpNoZOrder, swpNoActivate = 0x0004, 0x0010
	procSetWindowPos.Call(hwnd, 0, uintptr(x), uintptr(y), uintptr(ww), uintptr(wh), swpNoZOrder|swpNoActivate)
	if fit < 0.999 {
		z := strconv.FormatFloat(fit, 'f', 3, 64)
		w.Init("(function(){var z=function(){document.documentElement.style.zoom='" + z + "'};" +
			"if(document.documentElement)z();document.addEventListener('DOMContentLoaded',z)})()")
	}
}
