package core

import (
	"os"
	"path/filepath"
	"testing"
)

func TestEnsureServersKeepsPlayerServers(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "servers.dat")
	// a player's own list with our server further down
	root := nbtCompound{{"servers", tagList, nbtList{Elem: tagCompound, Items: []any{
		nbtCompound{{"name", tagString, "Мой сервер"}, {"ip", tagString, "mc.example.com"}, {"hidden", tagByte, int8(0)}},
		nbtCompound{{"name", tagString, "old"}, {"ip", tagString, Servers[0].Address()}, {"icon", tagString, "AAAA"}},
	}}}}
	f, _ := os.Create(path)
	if err := writeNBT(f, root); err != nil {
		t.Fatal(err)
	}
	f.Close()

	for i := 0; i < 2; i++ { // running twice must not duplicate anything
		if err := EnsureServers(dir); err != nil {
			t.Fatal(err)
		}
	}
	f, _ = os.Open(path)
	got, err := readNBT(f)
	f.Close()
	if err != nil {
		t.Fatal(err)
	}
	l := got[0].Value.(nbtList)
	if len(l.Items) != 2 {
		t.Fatalf("want 2 servers, got %d", len(l.Items))
	}
	first := l.Items[0].(nbtCompound)
	if first.str("ip") != "n1.pg19.su:25795" || first.str("name") != Servers[0].Name || first.str("icon") != "AAAA" {
		t.Fatalf("featured server wrong: %+v", first)
	}
	if l.Items[1].(nbtCompound).str("ip") != "mc.example.com" {
		t.Fatalf("player server lost")
	}
}

func TestEnsureServersNewFile(t *testing.T) {
	dir := t.TempDir()
	if err := EnsureServers(dir); err != nil {
		t.Fatal(err)
	}
	f, _ := os.Open(filepath.Join(dir, "servers.dat"))
	defer f.Close()
	got, err := readNBT(f)
	if err != nil || len(got[0].Value.(nbtList).Items) != 1 {
		t.Fatalf("bad file: %v %+v", err, got)
	}
}
