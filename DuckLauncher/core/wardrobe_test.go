package core

import (
	"errors"
	"os"
	"testing"
)

// Runs against a test copy of the site: DUCK_SITE=http://127.0.0.1:8095 DUCK_SITE_USER=nick DUCK_SITE_PASS=pass go test ./core -run Wardrobe
func TestWardrobeAgainstSite(t *testing.T) {
	user, pass := os.Getenv("DUCK_SITE_USER"), os.Getenv("DUCK_SITE_PASS")
	if os.Getenv("DUCK_SITE") == "" || user == "" {
		t.Skip("DUCK_SITE is not set")
	}
	if _, err := SiteLogin(user, "wrong-password"); err == nil || errors.Is(err, ErrSiteAuth) {
		t.Fatalf("wrong password: %v", err)
	}
	w, err := SiteLogin(user, pass)
	if err != nil || len(w.Token) != 48 {
		t.Fatalf("login: %v %+v", err, w)
	}
	c, err := CosmeticsCatalog()
	if err != nil || len(c.Capes) == 0 || len(c.Masks) == 0 || len(c.Skins) == 0 {
		t.Fatalf("catalog: %v", err)
	}
	w2, err := WardrobeSave(w.Token, map[string]string{"cape": "thunder", "mask": "", "mcNick": "TestDuck"})
	if err != nil || w2.Cape != "thunder" || w2.Mask != "" || w2.MCNick != "TestDuck" {
		t.Fatalf("save: %v %+v", err, w2)
	}
	if _, err := WardrobeSave(w.Token, map[string]string{"cape": "nope"}); err == nil {
		t.Fatal("unknown cape accepted")
	}
	png, _ := os.ReadFile("../../cosmetics/out/skins/inferno.png")
	w3, err := WardrobeUpload(w.Token, png, true)
	if err != nil || len(w3.Skin) < 10 || !w3.Slim {
		t.Fatalf("upload: %v %+v", err, w3)
	}
	SiteLogout(w.Token)
	if _, err := WardrobeGet(w.Token); !errors.Is(err, ErrSiteAuth) {
		t.Fatalf("after logout: %v", err)
	}
}
