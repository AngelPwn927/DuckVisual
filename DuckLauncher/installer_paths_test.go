package main

import (
	"path/filepath"
	"testing"
)

func TestInstallTarget(t *testing.T) {
	root := filepath.FromSlash("/games")
	cases := map[string]string{
		root:                                     filepath.Join(root, appFolder),
		filepath.Join(root, appFolder):           filepath.Join(root, appFolder),
		filepath.Join(root, "duck launcher"):     filepath.Join(root, "duck launcher"),
		`"` + root + `"`:                         filepath.Join(root, appFolder),
		"  " + root + string(filepath.Separator): filepath.Join(root, appFolder),
		"":                                       "",
		"relative/dir":                           "",
	}
	for in, want := range cases {
		if got := installTarget(in); got != want {
			t.Errorf("installTarget(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestSamePath(t *testing.T) {
	a := filepath.FromSlash("/Games/Duck Launcher")
	if !samePath(a, filepath.FromSlash("/games/duck launcher/")) {
		t.Error("paths differing only in case and a trailing slash must match")
	}
	if samePath(a, "") || samePath("", "") {
		t.Error("empty paths must never match")
	}
}
