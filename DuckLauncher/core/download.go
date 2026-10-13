package core

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"
)

var client = &http.Client{Timeout: 5 * time.Minute}

// Task is one file to download. Sha1 and Size are optional.
type Task struct {
	URL  string
	Path string
	Sha1 string
	Size int64
}

// Progress is reported to the UI while installing.
type Progress func(stage string, done, total int64)

func getJSON(url string, v any) error {
	var lastErr error
	for attempt := 0; attempt < 4; attempt++ {
		if attempt > 0 {
			time.Sleep(time.Duration(attempt) * time.Second)
		}
		req, _ := http.NewRequest("GET", url, nil)
		req.Header.Set("User-Agent", LauncherName+"/"+LauncherVersion)
		resp, err := client.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		func() {
			defer resp.Body.Close()
			if resp.StatusCode != 200 {
				lastErr = fmt.Errorf("%s: HTTP %d", url, resp.StatusCode)
				return
			}
			lastErr = json.NewDecoder(resp.Body).Decode(v)
		}()
		if lastErr == nil {
			return nil
		}
	}
	return lastErr
}

func fileOK(t Task) bool {
	st, err := os.Stat(t.Path)
	if err != nil || st.IsDir() {
		return false
	}
	if t.Size > 0 && st.Size() != t.Size {
		return false
	}
	return st.Size() > 0
}

func sha1File(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha1.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

func downloadOne(t Task, onBytes func(int64)) error {
	if err := os.MkdirAll(filepath.Dir(t.Path), 0o755); err != nil {
		return err
	}
	var lastErr error
	for attempt := 0; attempt < 4; attempt++ {
		if attempt > 0 {
			time.Sleep(time.Duration(attempt) * time.Second)
		}
		lastErr = func() error {
			req, _ := http.NewRequest("GET", t.URL, nil)
			req.Header.Set("User-Agent", LauncherName+"/"+LauncherVersion)
			resp, err := client.Do(req)
			if err != nil {
				return err
			}
			defer resp.Body.Close()
			if resp.StatusCode != 200 {
				return fmt.Errorf("%s: HTTP %d", t.URL, resp.StatusCode)
			}
			tmp := t.Path + ".part"
			f, err := os.Create(tmp)
			if err != nil {
				return err
			}
			h := sha1.New()
			buf := make([]byte, 64*1024)
			for {
				n, rerr := resp.Body.Read(buf)
				if n > 0 {
					if _, werr := f.Write(buf[:n]); werr != nil {
						f.Close()
						return werr
					}
					h.Write(buf[:n])
					if onBytes != nil {
						onBytes(int64(n))
					}
				}
				if rerr == io.EOF {
					break
				}
				if rerr != nil {
					f.Close()
					return rerr
				}
			}
			f.Close()
			if t.Sha1 != "" && hex.EncodeToString(h.Sum(nil)) != t.Sha1 {
				os.Remove(tmp)
				return fmt.Errorf("checksum mismatch for %s", filepath.Base(t.Path))
			}
			os.Remove(t.Path)
			return os.Rename(tmp, t.Path)
		}()
		if lastErr == nil {
			return nil
		}
	}
	return lastErr
}

// DownloadAll fetches missing files with a worker pool, reporting file counts.
func DownloadAll(stage string, tasks []Task, workers int, progress Progress) error {
	var missing []Task
	for _, t := range tasks {
		if !fileOK(t) {
			missing = append(missing, t)
		}
	}
	total := int64(len(missing))
	if progress != nil {
		progress(stage, 0, total)
	}
	if total == 0 {
		return nil
	}
	var done int64
	var firstErr error
	var mu sync.Mutex
	ch := make(chan Task)
	var wg sync.WaitGroup
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for t := range ch {
				if err := downloadOne(t, nil); err != nil {
					mu.Lock()
					if firstErr == nil {
						firstErr = err
					}
					mu.Unlock()
				}
				n := atomic.AddInt64(&done, 1)
				if progress != nil {
					progress(stage, n, total)
				}
			}
		}()
	}
	for _, t := range missing {
		ch <- t
	}
	close(ch)
	wg.Wait()
	return firstErr
}

// DownloadBig downloads one large file reporting bytes (used for Java).
func DownloadBig(stage string, t Task, progress Progress) error {
	if fileOK(t) {
		return nil
	}
	var done int64
	return downloadOne(t, func(n int64) {
		done += n
		if progress != nil {
			progress(stage, done, t.Size)
		}
	})
}
