package core

import "testing"

func TestDiagnoseText(t *testing.T) {
	cases := map[string]string{
		"Error occurred during initialization of VM\nCould not reserve enough space for 4194304KB object heap": "memory_down",
		"java.lang.OutOfMemoryError: Java heap space":                                                          "memory_up",
		"GLFW error 65542: WGL: The driver does not appear to support OpenGL":                                  "drivers",
		"# Problematic frame:\n# C  [atio6axx.dll+0x1a2b3c]":                                                   "drivers",
		"Caused by: org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error was encountered\nMixin apply for mod coolmod failed": "mods",
		"There is not enough space on the disk": "",
		"[Render thread/INFO]: Stopping!":       "-",
	}
	for in, want := range cases {
		d := diagnoseText(in)
		got := "-"
		if d != nil {
			got = d.Fix
		}
		if got != want {
			t.Errorf("diagnoseText(%q) fix = %q, want %q", in, got, want)
		}
	}
	if d := diagnoseText("Mixin apply for mod coolmod failed"); d == nil || d.Text == "" || !contains(d.Text, "coolmod") {
		t.Errorf("mixin failure should name the mod, got %+v", d)
	}
}

func contains(s, sub string) bool {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return true
		}
	}
	return false
}
