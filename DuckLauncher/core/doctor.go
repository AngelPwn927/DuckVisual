package core

import (
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

// Diagnosis explains in plain Russian why the game closed and which one-click fix may help.
type Diagnosis struct {
	Title string `json:"title"`
	Text  string `json:"text"`
	Fix   string `json:"fix"` // memory_up, memory_down, drivers, mods, potato or ""
}

var mixinMod = regexp.MustCompile(`(?i)mixin apply for mod ([a-z0-9_\-]+) failed|from mod ([a-z0-9_\-]+)\] .*failed`)

// tail returns up to n last bytes of a file.
func tail(path string, n int64) string {
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()
	if st, err := f.Stat(); err == nil && st.Size() > n {
		f.Seek(st.Size()-n, io.SeekStart)
	}
	b, _ := io.ReadAll(f)
	return string(b)
}

// newest returns the newest file in dir matching pattern written after since.
func newest(dir, pattern string, since time.Time) string {
	matches, _ := filepath.Glob(filepath.Join(dir, pattern))
	best, bestT := "", since
	for _, m := range matches {
		if st, err := os.Stat(m); err == nil && st.ModTime().After(bestT) {
			best, bestT = m, st.ModTime()
		}
	}
	return best
}

// Diagnose reads the game output, a fresh crash report and a JVM crash log (hs_err) and
// recognizes the usual reasons a game dies on weak or misconfigured PCs.
func Diagnose(logPath, gameDir string, started time.Time) *Diagnosis {
	text := tail(logPath, 256<<10)
	if cr := newest(filepath.Join(gameDir, "crash-reports"), "crash-*.txt", started); cr != "" {
		text += "\n" + tail(cr, 128<<10)
	}
	if hs := newest(gameDir, "hs_err_pid*.log", started); hs != "" {
		text += "\n" + tail(hs, 64<<10)
	}
	return diagnoseText(text)
}

func diagnoseText(text string) *Diagnosis {
	has := func(needles ...string) bool {
		for _, n := range needles {
			if strings.Contains(text, n) {
				return true
			}
		}
		return false
	}
	switch {
	case has("Could not reserve enough space", "There is insufficient memory for the Java Runtime",
		"Cannot allocate memory", "Could not create the Java Virtual Machine", "Native memory allocation (malloc) failed"):
		return &Diagnosis{"Слишком много памяти для этого ПК",
			"Игре выделено больше оперативной памяти, чем Windows может отдать. Лаунчер поставит подходящий объём, и игра запустится.",
			"memory_down"}
	case has("java.lang.OutOfMemoryError"):
		return &Diagnosis{"Игре не хватило памяти",
			"Миру, модам или ресурспаку стало тесно в выделенной памяти. Лаунчер добавит памяти игре.",
			"memory_up"}
	case has("WGL: The driver does not appear to support OpenGL", "GLFW error 65542", "GLFW error 65543",
		"Pixel format not accelerated", "OpenGL 3.2", "Couldn't set pixel format"):
		return &Diagnosis{"Драйвер видеокарты не подходит",
			"Видеокарта или её драйвер не поддерживает OpenGL 3.2, без которого Minecraft не запускается. Чаще всего драйвер просто не установлен или очень старый — обнови его через Центр обновления Windows или с сайта производителя ноутбука/видеокарты.",
			"drivers"}
	case has("atio6axx.dll", "atioglxx.dll", "ig9icd64.dll", "ig75icd64.dll", "ig8icd64.dll", "igxelpicd64.dll", "nvoglv64.dll", "nvoglv32.dll"):
		return &Diagnosis{"Игра упала в драйвере видеокарты",
			"Сбой случился внутри драйвера видеокарты. Обнови драйвер — обычно это решает проблему. Пока можно играть на профиле «Тапок»: он меньше нагружает видеокарту.",
			"drivers"}
	case has("There is not enough space on the disk", "No space left on device", "Недостаточно места на диске"):
		return &Diagnosis{"Закончилось место на диске",
			"Игре некуда сохранять мир и файлы. Освободи хотя бы 2 ГБ на диске C: и запусти снова.",
			""}
	case has("Incompatible mods found", "Mod resolution encountered an incompatible mod set", "Mixin apply", "MixinApplyError", "MixinTransformerError", "InvalidInjectionException"):
		d := &Diagnosis{"Конфликт модов",
			"Один из модов не подходит к этой версии игры или к другому моду. Открой «Моды», удали или обнови мод, который ставил сам.",
			"mods"}
		if m := mixinMod.FindStringSubmatch(text); m != nil {
			name := m[1]
			if name == "" {
				name = m[2]
			}
			d.Text = "Мод «" + name + "» не подходит к этой версии игры или конфликтует с другим модом. Открой «Моды» и удали или обнови его."
		}
		return d
	case has("EXCEPTION_ACCESS_VIOLATION"):
		return &Diagnosis{"Игра упала из-за сбоя в системе",
			"Чаще всего это старый драйвер видеокарты или программа поверх игры (оверлей записи экрана, «ускорители»). Обнови драйвер и закрой лишние программы.",
			"drivers"}
	}
	return nil
}
