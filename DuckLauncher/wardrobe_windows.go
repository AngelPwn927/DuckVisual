//go:build windows

package main

import (
	"encoding/base64"
	"errors"

	"ducklauncher/core"
)

// siteInfo is the "Гардероб" tab state: the duckvisual.ru account and what it wears.
type siteInfo struct {
	Site     string         `json:"site"`
	Wardrobe *core.Wardrobe `json:"wardrobe"` // nil = not signed in
	Catalog  *core.Catalog  `json:"catalog"`
	Error    string         `json:"error"`
	Notice   string         `json:"notice"` // e.g. the Minecraft nick is taken by someone else
}

func (a *app) siteToken() (token, mcName string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.cfg.Account != nil {
		mcName = a.cfg.Account.Name
	}
	return a.cfg.SiteToken, mcName
}

func (a *app) forgetSite() {
	a.mu.Lock()
	a.cfg.SiteToken, a.cfg.SiteLogin = "", ""
	a.cfg.Save()
	a.mu.Unlock()
}

// bindNick ties the site account to the nickname the game runs with, so the mod finds the player.
func bindNick(token, mcName string, w *core.Wardrobe) (*core.Wardrobe, string) {
	if mcName == "" || w.MCNick == mcName {
		return w, ""
	}
	nw, err := core.WardrobeSave(token, map[string]string{"mcNick": mcName})
	if err != nil {
		return w, "Ник «" + mcName + "» не привязан: " + err.Error()
	}
	return nw, ""
}

func (a *app) site() siteInfo {
	out := siteInfo{Site: core.SiteURL}
	if c, err := core.CosmeticsCatalog(); err == nil {
		out.Catalog = c
	} else {
		out.Error = err.Error()
	}
	token, mcName := a.siteToken()
	if token == "" {
		return out
	}
	w, err := core.WardrobeGet(token)
	if errors.Is(err, core.ErrSiteAuth) {
		a.forgetSite()
		return out
	}
	if err != nil {
		out.Error = err.Error()
		return out
	}
	out.Wardrobe, out.Notice = bindNick(token, mcName, w)
	return out
}

func (a *app) siteLogin(login, password string) siteInfo {
	w, err := core.SiteLogin(login, password)
	if err != nil {
		return siteInfo{Site: core.SiteURL, Error: err.Error()}
	}
	a.mu.Lock()
	a.cfg.SiteToken, a.cfg.SiteLogin = w.Token, w.Login
	a.cfg.Save()
	a.mu.Unlock()
	return a.site()
}

func (a *app) siteLogout() {
	token, _ := a.siteToken()
	if token != "" {
		go core.SiteLogout(token)
	}
	a.forgetSite()
}

// wardrobeSave wears items: keys skin, cape, mask ("" takes it off) and slim ("1"/"0").
func (a *app) wardrobeSave(changes map[string]string) (*core.Wardrobe, error) {
	token, _ := a.siteToken()
	if token == "" {
		return nil, core.ErrSiteAuth
	}
	delete(changes, "mcNick")
	w, err := core.WardrobeSave(token, changes)
	if errors.Is(err, core.ErrSiteAuth) {
		a.forgetSite()
	}
	return w, err
}

// wardrobeUpload takes a PNG picked in the window (base64) and wears it as the skin.
func (a *app) wardrobeUpload(b64 string, slim bool) (*core.Wardrobe, error) {
	token, _ := a.siteToken()
	if token == "" {
		return nil, core.ErrSiteAuth
	}
	png, err := base64.StdEncoding.DecodeString(b64)
	if err != nil {
		return nil, errors.New("не получилось прочитать файл")
	}
	w, err := core.WardrobeUpload(token, png, slim)
	if errors.Is(err, core.ErrSiteAuth) {
		a.forgetSite()
	}
	return w, err
}

// bindNickOnLaunch makes sure the site knows the nickname the game is about to start with.
func (a *app) bindNickOnLaunch() {
	token, mcName := a.siteToken()
	if token == "" || mcName == "" {
		return
	}
	go func() {
		if w, err := core.WardrobeGet(token); err == nil {
			bindNick(token, mcName, w)
		}
	}()
}

func (a *app) setPvPPack(on bool) {
	a.mu.Lock()
	a.cfg.PvPPack = on
	a.cfg.Save()
	a.mu.Unlock()
}

