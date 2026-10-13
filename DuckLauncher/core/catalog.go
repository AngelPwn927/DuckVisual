package core

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
)

// CatalogMod is one search result shown in the "Моды" tab.
type CatalogMod struct {
	Source      string `json:"source"` // "modrinth" or "curseforge"
	ID          string `json:"id"`
	Slug        string `json:"slug"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Author      string `json:"author"`
	Icon        string `json:"icon"`
	Downloads   int64  `json:"downloads"`
	URL         string `json:"url"`
	Installed   bool   `json:"installed"`
	Auto        bool   `json:"auto"` // installed by the launcher itself
}

// UserMod is a mod the player installed from the catalog.
type UserMod struct {
	Source string `json:"source"`
	ID     string `json:"id"`
	Title  string `json:"title"`
	Icon   string `json:"icon"`
	File   string `json:"file"`
	Dep    bool   `json:"dep"` // pulled in as a dependency
}

type userMods struct {
	Mods []UserMod `json:"mods"`
}

var userModsMu sync.Mutex

func userModsPath(gameDir string) string { return filepath.Join(gameDir, "mods", ".duck-user.json") }

func loadUserMods(gameDir string) *userMods {
	var u userMods
	readJSON(userModsPath(gameDir), &u)
	// forget mods the player deleted by hand
	kept := u.Mods[:0]
	for _, m := range u.Mods {
		if _, err := os.Stat(filepath.Join(gameDir, "mods", m.File)); err == nil {
			kept = append(kept, m)
		}
	}
	u.Mods = kept
	return &u
}

func (u *userMods) save(gameDir string) error {
	os.MkdirAll(filepath.Join(gameDir, "mods"), 0o755)
	b, _ := json.MarshalIndent(u, "", "  ")
	return os.WriteFile(userModsPath(gameDir), b, 0o644)
}

func (u *userMods) has(source, id string) bool {
	for _, m := range u.Mods {
		if m.Source == source && m.ID == id {
			return true
		}
	}
	return false
}

// InstalledMods lists the catalog mods in an instance.
func InstalledMods(gameDir string) []UserMod {
	userModsMu.Lock()
	defer userModsMu.Unlock()
	u := loadUserMods(gameDir)
	if u.Mods == nil {
		return []UserMod{}
	}
	return u.Mods
}

// SearchMods queries Modrinth or CurseForge for Fabric mods of a game version.
// sort is "relevance", "downloads" or "updated".
func SearchMods(source, query, gameVersion, sort, cfKey string, page int, gameDir string, perf map[string]bool) ([]CatalogMod, error) {
	var res []CatalogMod
	var err error
	if source == "curseforge" {
		res, err = cfSearch(query, gameVersion, sort, cfKey, page)
	} else {
		res, err = mrSearch(query, gameVersion, sort, page)
	}
	if err != nil {
		return nil, err
	}
	userModsMu.Lock()
	u := loadUserMods(gameDir)
	userModsMu.Unlock()
	for i := range res {
		res[i].Installed = u.has(res[i].Source, res[i].ID)
		res[i].Auto = isAutoMod(res[i].Source, res[i].ID, perf, gameDir)
	}
	return res, nil
}

// InstallMod downloads the newest Fabric build of a mod (and its required dependencies) into the instance.
// perf lists the performance mods the launcher installs itself (Fabric API is always installed).
func InstallMod(source, id, gameVersion, cfKey, gameDir string, perf map[string]bool, progress Progress) (string, error) {
	userModsMu.Lock()
	defer userModsMu.Unlock()
	u := loadUserMods(gameDir)
	mods := filepath.Join(gameDir, "mods")
	os.MkdirAll(mods, 0o755)

	type item struct {
		id  string
		dep bool
	}
	queue := []item{{id, false}}
	seen := map[string]bool{}
	var first string
	for len(queue) > 0 {
		it := queue[0]
		queue = queue[1:]
		if seen[it.id] {
			continue
		}
		seen[it.id] = true
		if isAutoMod(source, it.id, perf, gameDir) {
			if it.dep {
				continue
			}
			return "", fmt.Errorf("этот мод уже ставит сам лаунчер (Настройки → Моды оптимизации)")
		}
		if it.dep && u.has(source, it.id) {
			continue
		}
		var f *modFile
		var err error
		if source == "curseforge" {
			f, err = cfLatest(it.id, gameVersion, cfKey)
		} else {
			f, err = mrLatest(it.id, gameVersion)
		}
		if err != nil {
			if it.dep {
				continue
			}
			return "", err
		}
		if f == nil {
			if it.dep {
				continue
			}
			return "", fmt.Errorf("у этого мода нет версии для Fabric %s", gameVersion)
		}
		if f.URL == "" {
			if it.dep {
				continue
			}
			return "", fmt.Errorf("автор «%s» запретил скачивание в сторонних лаунчерах — скачай со страницы мода", f.Title)
		}
		name := filepath.Base(f.File)
		if name == "." || name == "/" || strings.ContainsAny(name, `\/:`) {
			return "", fmt.Errorf("неверное имя файла: %q", f.File)
		}
		// replace an older file of the same mod
		for i, m := range u.Mods {
			if m.Source == source && m.ID == it.id {
				if m.File != name {
					os.Remove(filepath.Join(mods, m.File))
				}
				u.Mods = append(u.Mods[:i], u.Mods[i+1:]...)
				break
			}
		}
		if err := DownloadAll("mod", []Task{{URL: f.URL, Sha1: f.Sha1, Size: f.Size, Path: filepath.Join(mods, name)}}, 1, progress); err != nil {
			return "", err
		}
		u.Mods = append(u.Mods, UserMod{Source: source, ID: it.id, Title: f.Title, Icon: f.Icon, File: name, Dep: it.dep})
		if !it.dep {
			first = f.Title
		}
		for _, d := range f.Deps {
			queue = append(queue, item{d, true})
		}
	}
	return first, u.save(gameDir)
}

// isAutoMod reports whether a mod is already installed by the launcher itself (Fabric API, Sodium,
// or a dependency of a performance mod).
func isAutoMod(source, id string, perf map[string]bool, gameDir string) bool {
	if source == "curseforge" {
		slug, ok := map[string]string{"306612": "fabric-api", "394468": "sodium"}[id]
		return ok && (slug == "fabric-api" || perf[slug])
	}
	if id == "P7dR8mSH" {
		return true
	}
	for _, pm := range PerfMods {
		if pm.ID == id || pm.Slug == id {
			return perf[pm.Slug] && (pm.Needs == "" || perf[pm.Needs])
		}
	}
	var m managed
	readJSON(filepath.Join(gameDir, "mods", ".duck-managed.json"), &m)
	_, ok := m.Files[id]
	return ok
}

// RemoveMod deletes a catalog mod from the instance.
func RemoveMod(gameDir, source, id string) error {
	userModsMu.Lock()
	defer userModsMu.Unlock()
	u := loadUserMods(gameDir)
	for i, m := range u.Mods {
		if m.Source == source && m.ID == id {
			os.Remove(filepath.Join(gameDir, "mods", m.File))
			u.Mods = append(u.Mods[:i], u.Mods[i+1:]...)
			return u.save(gameDir)
		}
	}
	return nil
}

type modFile struct {
	Title, Icon, URL, File, Sha1 string
	Size                         int64
	Deps                         []string
}

/* ---------------- Modrinth ---------------- */

func mrSearch(query, gameVersion, sort string, page int) ([]CatalogMod, error) {
	index := map[string]string{"downloads": "downloads", "updated": "updated"}[sort]
	if index == "" {
		index = "relevance"
		if strings.TrimSpace(query) == "" {
			index = "downloads"
		}
	}
	facets := `[["project_type:mod"],["categories:fabric"],["versions:` + gameVersion + `"]]`
	q := url.Values{"query": {query}, "facets": {facets}, "index": {index},
		"limit": {"20"}, "offset": {strconv.Itoa(page * 20)}}
	var r struct {
		Hits []struct {
			ProjectID   string `json:"project_id"`
			Slug        string `json:"slug"`
			Title       string `json:"title"`
			Description string `json:"description"`
			Author      string `json:"author"`
			IconURL     string `json:"icon_url"`
			Downloads   int64  `json:"downloads"`
		} `json:"hits"`
	}
	if err := getJSON("https://api.modrinth.com/v2/search?"+q.Encode(), &r); err != nil {
		return nil, fmt.Errorf("Modrinth недоступен: %w", err)
	}
	out := []CatalogMod{}
	for _, h := range r.Hits {
		out = append(out, CatalogMod{Source: "modrinth", ID: h.ProjectID, Slug: h.Slug, Title: h.Title, Description: h.Description,
			Author: h.Author, Icon: h.IconURL, Downloads: h.Downloads, URL: "https://modrinth.com/mod/" + h.Slug})
	}
	return out, nil
}

func mrLatest(id, gameVersion string) (*modFile, error) {
	var p struct {
		Title   string `json:"title"`
		IconURL string `json:"icon_url"`
	}
	if err := getJSON("https://api.modrinth.com/v2/project/"+url.PathEscape(id), &p); err != nil {
		return nil, err
	}
	q := url.Values{"game_versions": {`["` + gameVersion + `"]`}, "loaders": {`["fabric"]`}}
	var versions []struct {
		Files []struct {
			URL      string `json:"url"`
			Filename string `json:"filename"`
			Primary  bool   `json:"primary"`
			Size     int64  `json:"size"`
			Hashes   struct {
				Sha1 string `json:"sha1"`
			} `json:"hashes"`
		} `json:"files"`
		Dependencies []struct {
			ProjectID      string `json:"project_id"`
			DependencyType string `json:"dependency_type"`
		} `json:"dependencies"`
	}
	if err := getJSON("https://api.modrinth.com/v2/project/"+url.PathEscape(id)+"/version?"+q.Encode(), &versions); err != nil {
		return nil, err
	}
	if len(versions) == 0 || len(versions[0].Files) == 0 {
		return nil, nil
	}
	v := versions[0]
	f := v.Files[0]
	for _, x := range v.Files {
		if x.Primary {
			f = x
		}
	}
	mf := &modFile{Title: p.Title, Icon: p.IconURL, URL: f.URL, File: f.Filename, Sha1: f.Hashes.Sha1, Size: f.Size}
	for _, d := range v.Dependencies {
		if d.DependencyType == "required" && d.ProjectID != "" {
			mf.Deps = append(mf.Deps, d.ProjectID)
		}
	}
	return mf, nil
}

/* ---------------- CurseForge ---------------- */

// ErrNoCFKey tells the UI to ask for a CurseForge API key.
var ErrNoCFKey = fmt.Errorf("для CurseForge нужен API-ключ: Настройки → CurseForge")

func cfGet(path, key string, v any) error {
	if strings.TrimSpace(key) == "" {
		return ErrNoCFKey
	}
	req, _ := http.NewRequest("GET", "https://api.curseforge.com/v1"+path, nil)
	req.Header.Set("x-api-key", strings.TrimSpace(key))
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", LauncherName+"/"+LauncherVersion)
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("CurseForge недоступен: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode == 401 || resp.StatusCode == 403 {
		return fmt.Errorf("CurseForge не принял API-ключ, проверь его в настройках")
	}
	if resp.StatusCode != 200 {
		return fmt.Errorf("CurseForge: HTTP %d", resp.StatusCode)
	}
	return json.NewDecoder(resp.Body).Decode(v)
}

const cfFabric = "4" // modLoaderType

func cfSearch(query, gameVersion, sort, key string, page int) ([]CatalogMod, error) {
	field := map[string]string{"downloads": "6", "updated": "3", "relevance": "2"}[sort]
	if field == "" {
		field = "2"
	}
	q := url.Values{"gameId": {"432"}, "classId": {"6"}, "gameVersion": {gameVersion}, "modLoaderType": {cfFabric},
		"searchFilter": {query}, "sortField": {field}, "sortOrder": {"desc"}, "pageSize": {"20"}, "index": {strconv.Itoa(page * 20)}}
	var r struct {
		Data []struct {
			ID            int64  `json:"id"`
			Name          string `json:"name"`
			Slug          string `json:"slug"`
			Summary       string `json:"summary"`
			DownloadCount int64  `json:"downloadCount"`
			Links         struct {
				WebsiteURL string `json:"websiteUrl"`
			} `json:"links"`
			Logo struct {
				ThumbnailURL string `json:"thumbnailUrl"`
			} `json:"logo"`
			Authors []struct {
				Name string `json:"name"`
			} `json:"authors"`
		} `json:"data"`
	}
	if err := cfGet("/mods/search?"+q.Encode(), key, &r); err != nil {
		return nil, err
	}
	out := []CatalogMod{}
	for _, m := range r.Data {
		c := CatalogMod{Source: "curseforge", ID: strconv.FormatInt(m.ID, 10), Slug: m.Slug, Title: m.Name, Description: m.Summary,
			Icon: m.Logo.ThumbnailURL, Downloads: m.DownloadCount, URL: m.Links.WebsiteURL}
		if len(m.Authors) > 0 {
			c.Author = m.Authors[0].Name
		}
		out = append(out, c)
	}
	return out, nil
}

func cfLatest(id, gameVersion, key string) (*modFile, error) {
	var p struct {
		Data struct {
			Name string `json:"name"`
			Logo struct {
				ThumbnailURL string `json:"thumbnailUrl"`
			} `json:"logo"`
		} `json:"data"`
	}
	if err := cfGet("/mods/"+url.PathEscape(id), key, &p); err != nil {
		return nil, err
	}
	q := url.Values{"gameVersion": {gameVersion}, "modLoaderType": {cfFabric}, "pageSize": {"1"}}
	var r struct {
		Data []struct {
			FileName    string `json:"fileName"`
			DownloadURL string `json:"downloadUrl"`
			FileLength  int64  `json:"fileLength"`
			Hashes      []struct {
				Value string `json:"value"`
				Algo  int    `json:"algo"`
			} `json:"hashes"`
			Dependencies []struct {
				ModID        int64 `json:"modId"`
				RelationType int   `json:"relationType"`
			} `json:"dependencies"`
		} `json:"data"`
	}
	if err := cfGet("/mods/"+url.PathEscape(id)+"/files?"+q.Encode(), key, &r); err != nil {
		return nil, err
	}
	if len(r.Data) == 0 {
		return nil, nil
	}
	f := r.Data[0]
	mf := &modFile{Title: p.Data.Name, Icon: p.Data.Logo.ThumbnailURL, URL: f.DownloadURL, File: f.FileName, Size: f.FileLength}
	for _, h := range f.Hashes {
		if h.Algo == 1 {
			mf.Sha1 = h.Value
		}
	}
	for _, d := range f.Dependencies {
		if d.RelationType == 3 { // required
			mf.Deps = append(mf.Deps, strconv.FormatInt(d.ModID, 10))
		}
	}
	return mf, nil
}
