package core

import "testing"

func TestPackList(t *testing.T) {
	cases := []struct {
		cur  string
		on   bool
		want string
	}{
		{"", true, `["vanilla","file/Duck-PvP.zip"]`},
		{"", false, ""},
		{`["vanilla","fabric"]`, true, `["vanilla","fabric","file/Duck-PvP.zip"]`},
		{`["vanilla","file/Duck-PvP.zip","file/Other.zip"]`, true, `["vanilla","file/Other.zip","file/Duck-PvP.zip"]`},
		{`["vanilla","fabric","file/Duck-PvP.zip"]`, false, `["vanilla","fabric"]`},
		{`["vanilla","fabric"]`, false, `["vanilla","fabric"]`},
	}
	for _, c := range cases {
		if got := packList(c.cur, c.on); got != c.want {
			t.Errorf("packList(%q, %v) = %q, want %q", c.cur, c.on, got, c.want)
		}
	}
}
