package core

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

// SiteURL is duckvisual.ru; DUCK_SITE overrides it for tests.
var SiteURL = func() string {
	if s := os.Getenv("DUCK_SITE"); s != "" {
		return strings.TrimRight(s, "/")
	}
	return "https://duckvisual.ru"
}()

var siteClient = &http.Client{Timeout: 20 * time.Second}

// Wardrobe is what the site account wears. Items are catalog ids ("" = nothing); a custom skin is "u:<hash>".
type Wardrobe struct {
	Login   string `json:"login"`
	Avatar  string `json:"avatar"`
	MCNick  string `json:"mcNick"`
	Skin    string `json:"skin"`
	Slim    bool   `json:"slim"`
	Cape    string `json:"cape"`
	Mask    string `json:"mask"`
	SkinURL string `json:"skinUrl"`
	Token   string `json:"token,omitempty"`
}

// CosmeticItem is one entry of the site catalog.
type CosmeticItem struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Rarity string `json:"rarity"`
}

type Catalog struct {
	Capes []CosmeticItem `json:"capes"`
	Masks []CosmeticItem `json:"masks"`
	Skins []CosmeticItem `json:"skins"`
}

// ErrSiteAuth means the saved site token no longer works: the user has to log in again.
var ErrSiteAuth = fmt.Errorf("войди в аккаунт duckvisual.ru заново")

func siteDo(req *http.Request) (*Wardrobe, error) {
	req.Header.Set("User-Agent", LauncherName+"/"+LauncherVersion)
	resp, err := siteClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("сайт duckvisual.ru недоступен, проверь интернет")
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	var out struct {
		Wardrobe
		Error string `json:"error"`
	}
	json.Unmarshal(b, &out)
	if resp.StatusCode == 401 && out.Token == "" && !strings.Contains(out.Error, "пароль") {
		return nil, ErrSiteAuth
	}
	if out.Error != "" {
		return nil, fmt.Errorf("%s", out.Error)
	}
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("сайт ответил HTTP %d", resp.StatusCode)
	}
	w := out.Wardrobe
	return &w, nil
}

func sitePost(form url.Values) (*Wardrobe, error) {
	req, _ := http.NewRequest("POST", SiteURL+"/api/wardrobe.php", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	return siteDo(req)
}

// SiteLogin signs in with the nickname and password of a duckvisual.ru account. The result carries the token.
func SiteLogin(login, password string) (*Wardrobe, error) {
	login = strings.TrimSpace(login)
	if login == "" || password == "" {
		return nil, fmt.Errorf("введи ник и пароль от сайта")
	}
	return sitePost(url.Values{"action": {"login"}, "login": {login}, "password": {password}})
}

func WardrobeGet(token string) (*Wardrobe, error) {
	return sitePost(url.Values{"action": {"get"}, "token": {token}})
}

// WardrobeSave changes only the fields present in changes (mcNick, skin, cape, mask, slim).
func WardrobeSave(token string, changes map[string]string) (*Wardrobe, error) {
	form := url.Values{"action": {"save"}, "token": {token}}
	for _, k := range []string{"mcNick", "skin", "cape", "mask", "slim"} {
		if v, ok := changes[k]; ok {
			form.Set(k, v)
		}
	}
	return sitePost(form)
}

// WardrobeUpload sends a custom skin (PNG 64x64) and wears it.
func WardrobeUpload(token string, png []byte, slim bool) (*Wardrobe, error) {
	if len(png) == 0 || len(png) > 64<<10 {
		return nil, fmt.Errorf("скин должен быть PNG до 64 КБ")
	}
	var body bytes.Buffer
	mw := multipart.NewWriter(&body)
	mw.WriteField("action", "upload")
	mw.WriteField("token", token)
	if slim {
		mw.WriteField("slim", "1")
	}
	fw, _ := mw.CreateFormFile("skin", "skin.png")
	fw.Write(png)
	mw.Close()
	req, _ := http.NewRequest("POST", SiteURL+"/api/wardrobe.php", &body)
	req.Header.Set("Content-Type", mw.FormDataContentType())
	if _, err := siteDo(req); err != nil {
		return nil, err
	}
	return WardrobeGet(token)
}

func SiteLogout(token string) {
	sitePost(url.Values{"action": {"logout"}, "token": {token}})
}

// CosmeticsCatalog lists the capes, masks and skins on the site.
func CosmeticsCatalog() (*Catalog, error) {
	var c Catalog
	if err := getJSON(SiteURL+"/cosmetics/catalog.json", &c); err != nil {
		return nil, fmt.Errorf("каталог не загрузился: %w", err)
	}
	return &c, nil
}
