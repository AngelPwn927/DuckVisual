package core

import (
	"bytes"
	"crypto/md5"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"path/filepath"
	"regexp"
	"strings"
)

// Account is what the game needs to start.
type Account struct {
	Type        string `json:"type"` // "offline" or "ely"
	Name        string `json:"name"`
	UUID        string `json:"uuid"`
	AccessToken string `json:"accessToken"`
	ClientToken string `json:"clientToken"`
	ElyLogin    string `json:"elyLogin"`
}

var nameRe = regexp.MustCompile(`^[A-Za-z0-9_]{3,16}$`)

func ValidName(n string) bool { return nameRe.MatchString(n) }

// OfflineUUID matches what vanilla servers compute for offline players: UUID v3 of "OfflinePlayer:<name>".
func OfflineUUID(name string) string {
	h := md5.Sum([]byte("OfflinePlayer:" + name))
	h[6] = (h[6] & 0x0f) | 0x30
	h[8] = (h[8] & 0x3f) | 0x80
	return hex.EncodeToString(h[:])
}

func OfflineAccount(name string) (*Account, error) {
	if !ValidName(name) {
		return nil, fmt.Errorf("ник: 3-16 символов, латиница, цифры и _")
	}
	return &Account{Type: "offline", Name: name, UUID: OfflineUUID(name), AccessToken: "0"}, nil
}

func randomToken() string {
	b := make([]byte, 16)
	rand.Read(b)
	return hex.EncodeToString(b)
}

type yggResponse struct {
	AccessToken     string `json:"accessToken"`
	ClientToken     string `json:"clientToken"`
	SelectedProfile struct {
		ID   string `json:"id"`
		Name string `json:"name"`
	} `json:"selectedProfile"`
	ErrorMessage string `json:"errorMessage"`
}

func yggPost(url string, body any) (*yggResponse, error) {
	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", url, bytes.NewReader(b))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", LauncherName+"/"+LauncherVersion)
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	var r yggResponse
	json.NewDecoder(resp.Body).Decode(&r)
	if resp.StatusCode != 200 {
		if r.ErrorMessage != "" {
			return nil, fmt.Errorf("%s", r.ErrorMessage)
		}
		return nil, fmt.Errorf("Ely.by: HTTP %d", resp.StatusCode)
	}
	return &r, nil
}

// ElyLogin signs in with an Ely.by account (login/email + password, "password:totp" for 2FA).
func ElyLogin(login, password string) (*Account, error) {
	ct := randomToken()
	r, err := yggPost("https://authserver.ely.by/auth/authenticate", map[string]any{
		"username": login, "password": password, "clientToken": ct, "requestUser": true,
	})
	if err != nil {
		return nil, err
	}
	if r.SelectedProfile.ID == "" {
		return nil, fmt.Errorf("на аккаунте нет игрового профиля")
	}
	return &Account{Type: "ely", Name: r.SelectedProfile.Name, UUID: r.SelectedProfile.ID,
		AccessToken: r.AccessToken, ClientToken: ct, ElyLogin: login}, nil
}

// ElyRefresh renews the access token before launching (tokens expire).
func ElyRefresh(a *Account) error {
	r, err := yggPost("https://authserver.ely.by/auth/refresh", map[string]any{
		"accessToken": a.AccessToken, "clientToken": a.ClientToken, "requestUser": true,
	})
	if err != nil {
		return err
	}
	a.AccessToken = r.AccessToken
	if r.SelectedProfile.Name != "" {
		a.Name = r.SelectedProfile.Name
		a.UUID = r.SelectedProfile.ID
	}
	return nil
}

// EnsureAuthlibInjector downloads authlib-injector, which redirects Minecraft's auth/skins to Ely.by.
func EnsureAuthlibInjector(progress Progress) (string, error) {
	var latest struct {
		DownloadURL string `json:"download_url"`
		Version     string `json:"version"`
		Checksums   struct {
			Sha256 string `json:"sha256"`
		} `json:"checksums"`
	}
	path := filepath.Join(LibrariesDir(), "authlib-injector", "authlib-injector.jar")
	if fileOK(Task{Path: path}) {
		return path, nil
	}
	if err := getJSON("https://authlib-injector.yushi.moe/artifact/latest.json", &latest); err != nil {
		return "", fmt.Errorf("authlib-injector: %w", err)
	}
	if !strings.HasPrefix(latest.DownloadURL, "https://") {
		return "", fmt.Errorf("authlib-injector: bad download url")
	}
	return path, DownloadAll("auth", []Task{{URL: latest.DownloadURL, Path: path}}, 1, progress)
}
