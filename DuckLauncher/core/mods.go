package core

import (
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strings"
)

// DuckVisualJar is the mod embedded into the launcher binary (set by main via go:embed).
var DuckVisualJar []byte

// OptionalMod is a performance mod the player can toggle in the settings.
type OptionalMod struct {
	Slug    string `json:"slug"`
	ID      string `json:"id"` // Modrinth project id (dependencies refer to projects by id)
	Name    string `json:"name"`
	About   string `json:"about"`
	Group   string `json:"group"`
	Default bool   `json:"default"`
	Needs   string `json:"needs,omitempty"` // only installed together with this mod
}

// PerfMods are Modrinth projects that make the game run better on weak PCs.
// Each is installed only if it exists for the chosen version, with its required dependencies.
var PerfMods = []OptionalMod{
	{"sodium", "AANobbMI", "Sodium", "Новый движок рендера: обычно в 2–3 раза больше FPS", "fps", true, ""},
	{"sodium-extra", "PtjYWJkn", "Sodium Extra", "Ещё настройки графики: выключение анимаций, частиц, тумана и т.д.", "fps", true, "sodium"},
	{"reeses-sodium-options", "Bh37bMuy", "Reese's Sodium Options", "Удобное меню настроек Sodium", "fps", true, "sodium"},
	{"moreculling", "51shyZVL", "MoreCulling", "Не рисует скрытые грани листвы, стекла и блоков", "fps", true, ""},
	{"entityculling", "NNAgCjsB", "EntityCulling", "Не рисует мобов и сундуки, которые не видно", "fps", true, ""},
	{"immediatelyfast", "5ZwdcRci", "ImmediatelyFast", "Ускоряет отрисовку HUD, текста и предметов", "fps", true, ""},
	{"badoptimizations", "g96Z4WVZ", "BadOptimizations", "Кэширует освещение, небо и цвета — меньше работы за кадр", "fps", true, ""},
	{"dynamic-fps", "LQ3K71Q1", "Dynamic FPS", "Снижает FPS в свёрнутой игре: ПК не греется, музыка в браузере не лагает", "fps", true, ""},
	{"lithium", "gvQqBUqZ", "Lithium", "Оптимизирует физику, ИИ мобов и тики мира", "cpu", true, ""},
	{"scalablelux", "Ps1zyz6x", "ScalableLux", "Быстрый движок освещения, чанки грузятся плавнее", "cpu", true, ""},
	{"clumps", "Wnxd13zP", "Clumps", "Склеивает сферы опыта — нет лагов на фермах", "cpu", true, ""},
	{"noisium", "KuNKN7d2", "Noisium", "Быстрее генерирует мир в одиночной игре", "cpu", true, ""},
	{"c2me-fabric", "VSNURh3q", "C2ME", "Многопоточная загрузка и генерация чанков (одиночная игра, бета)", "cpu", false, ""},
	{"ferrite-core", "uXXizFIs", "FerriteCore", "Сильно уменьшает потребление оперативной памяти", "mem", true, ""},
	{"modernfix", "nmDcB62a", "ModernFix", "Быстрый запуск игры и ещё меньше памяти", "mem", true, ""},
	{"ksyxis", "2ecVyZ49", "Ksyxis", "Мир загружается быстрее", "mem", true, ""},
	{"krypton", "fQEb0iXm", "Krypton", "Оптимизирует сеть — меньше нагрузка на серверах", "net", true, ""},
}

// DefaultMods is the toggle state of a fresh install.
func DefaultMods() map[string]bool {
	m := map[string]bool{}
	for _, pm := range PerfMods {
		m[pm.Slug] = pm.Default
	}
	return m
}

type modrinthVersion struct {
	VersionNumber string `json:"version_number"`
	Files         []struct {
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

// resolvedMod is a downloadable file plus the projects it needs.
type resolvedMod struct {
	Task
	Deps []string `json:"deps"`
}

// modrinthFile finds the newest Fabric build of a project (slug or id) for a game version.
func modrinthFile(project, gameVersion string) (*resolvedMod, error) {
	q := url.Values{}
	q.Set("game_versions", `["`+gameVersion+`"]`)
	q.Set("loaders", `["fabric"]`)
	var versions []modrinthVersion
	if err := getJSON("https://api.modrinth.com/v2/project/"+url.PathEscape(project)+"/version?"+q.Encode(), &versions); err != nil {
		return nil, err
	}
	if len(versions) == 0 || len(versions[0].Files) == 0 {
		return nil, nil // not available for this version
	}
	f := versions[0].Files[0]
	for _, x := range versions[0].Files {
		if x.Primary {
			f = x
		}
	}
	r := &resolvedMod{Task: Task{URL: f.URL, Sha1: f.Hashes.Sha1, Size: f.Size, Path: f.Filename}}
	for _, d := range versions[0].Dependencies {
		if d.DependencyType == "required" && d.ProjectID != "" {
			r.Deps = append(r.Deps, d.ProjectID)
		}
	}
	return r, nil
}

// managed remembers which jars the launcher put into a mods folder, so it can update or
// remove them without touching mods the player added by hand.
type managed struct {
	Files map[string]string `json:"files"` // slug or project id -> file name
}

// EnsureMods installs Fabric API, the chosen performance mods with their dependencies and
// (on 1.21.4) Duck Visual.
func EnsureMods(version, gameDir string, enabled map[string]bool, progress Progress) error {
	mods := filepath.Join(gameDir, "mods")
	if err := os.MkdirAll(mods, 0o755); err != nil {
		return err
	}
	manifestPath := filepath.Join(mods, ".duck-managed.json")
	var m managed
	readJSON(manifestPath, &m)
	if m.Files == nil {
		m.Files = map[string]string{}
	}

	cachePath := filepath.Join(VersionsDir(), version, "mods-resolved-v2.json")
	var cache map[string]*resolvedMod
	readJSON(cachePath, &cache)
	if cache == nil {
		cache = map[string]*resolvedMod{}
	}

	// project ids of the mods we know, so a dependency on e.g. Sodium is not installed twice
	idToSlug := map[string]string{"P7dR8mSH": "fabric-api"}
	for _, pm := range PerfMods {
		idToSlug[pm.ID] = pm.Slug
	}
	// mods the player installed from the catalog already satisfy dependencies
	userIDs := map[string]bool{}
	userModsMu.Lock()
	for _, um := range loadUserMods(gameDir).Mods {
		if um.Source == "modrinth" {
			userIDs[um.ID] = true
		}
	}
	userModsMu.Unlock()

	queue := []string{"fabric-api"}
	for _, pm := range PerfMods {
		if enabled[pm.Slug] && (pm.Needs == "" || enabled[pm.Needs]) {
			queue = append(queue, pm.Slug)
		}
	}
	var tasks []Task
	next := map[string]string{}
	seen := map[string]bool{}
	for len(queue) > 0 {
		key := queue[0]
		queue = queue[1:]
		if s, ok := idToSlug[key]; ok {
			key = s
		}
		if seen[key] || userIDs[key] {
			continue
		}
		seen[key] = true
		t, ok := cache[key]
		if !ok {
			if progress != nil {
				progress("mods-resolve", 0, 1)
			}
			var err error
			t, err = modrinthFile(key, version)
			if err != nil {
				if key == "fabric-api" {
					return fmt.Errorf("Fabric API: %w", err)
				}
				continue // optional mod: skip on network errors
			}
			cache[key] = t
		}
		if t == nil {
			continue
		}
		next[key] = t.Path
		tasks = append(tasks, Task{URL: t.URL, Sha1: t.Sha1, Size: t.Size, Path: filepath.Join(mods, t.Path)})
		queue = append(queue, t.Deps...)
	}
	os.MkdirAll(filepath.Dir(cachePath), 0o755)
	if b, err := json.Marshal(cache); err == nil {
		os.WriteFile(cachePath, b, 0o644)
	}

	// remove managed jars that are disabled or replaced by a newer file
	for slug, file := range m.Files {
		if next[slug] != file && !isValue(next, file) && !strings.Contains(file, "/") && !strings.Contains(file, "\\") {
			os.Remove(filepath.Join(mods, file))
		}
	}
	if err := DownloadAll("mods", tasks, 4, progress); err != nil {
		return err
	}

	// Duck Visual only exists for 1.21.4; refresh it every launch so launcher updates ship it
	duck := filepath.Join(mods, "duck-visual.jar")
	if version == DuckVersion && len(DuckVisualJar) > 0 {
		if old, err := os.ReadFile(duck); err != nil || string(old) != string(DuckVisualJar) {
			if err := os.WriteFile(duck, DuckVisualJar, 0o644); err != nil {
				return err
			}
		}
		next["duck-visual"] = "duck-visual.jar"
	}
	// clean up the jars of launcher 1.0 (fixed names)
	entries, _ := os.ReadDir(mods)
	for _, e := range entries {
		n := e.Name()
		if (strings.HasPrefix(n, "fabric-api-0.119.4") || strings.HasPrefix(n, "sodium-mc1.21.4-0.6.13")) && !isValue(next, n) {
			os.Remove(filepath.Join(mods, n))
		}
	}
	m.Files = next
	b, _ := json.MarshalIndent(m, "", "  ")
	return os.WriteFile(manifestPath, b, 0o644)
}

func isValue(m map[string]string, v string) bool {
	for _, x := range m {
		if x == v {
			return true
		}
	}
	return false
}
