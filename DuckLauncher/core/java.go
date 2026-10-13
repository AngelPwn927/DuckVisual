package core

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

type adoptiumAsset struct {
	Binary struct {
		Package struct {
			Link     string `json:"link"`
			Size     int64  `json:"size"`
			Checksum string `json:"checksum"`
			Name     string `json:"name"`
		} `json:"package"`
	} `json:"binary"`
}

func javaExeName() string {
	if runtime.GOOS == "windows" {
		return "javaw.exe" // no console window
	}
	return "java"
}

func findJava(dir string) string {
	var found string
	filepath.WalkDir(dir, func(p string, d os.DirEntry, err error) error {
		if err != nil || found != "" {
			return nil
		}
		if !d.IsDir() && d.Name() == javaExeName() && filepath.Base(filepath.Dir(p)) == "bin" {
			found = p
		}
		return nil
	})
	return found
}

// javaFeature picks a Temurin release that satisfies the version's required Java.
func javaFeature(major int) int {
	for _, v := range []int{8, 11, 17, 21, 25} {
		if v >= major {
			return v
		}
	}
	return major
}

// EnsureJava downloads the Eclipse Temurin JRE a Minecraft version needs (8 for <=1.16, 17, 21, ...), once.
func EnsureJava(major int, progress Progress) (string, error) {
	feature := javaFeature(major)
	dir := filepath.Join(RuntimeDir(), fmt.Sprintf("java%d", feature))
	if j := findJava(dir); j != "" {
		return j, nil
	}
	osName := map[string]string{"windows": "windows", "linux": "linux", "darwin": "mac"}[runtime.GOOS]
	arch := map[string]string{"amd64": "x64", "arm64": "aarch64", "386": "x32"}[runtime.GOARCH]
	if osName == "" || arch == "" {
		return "", fmt.Errorf("unsupported platform %s/%s", runtime.GOOS, runtime.GOARCH)
	}
	var assets []adoptiumAsset
	url := fmt.Sprintf("https://api.adoptium.net/v3/assets/latest/%d/hotspot?architecture=%s&image_type=jre&os=%s&vendor=eclipse", feature, arch, osName)
	if err := getJSON(url, &assets); err != nil || len(assets) == 0 {
		return "", fmt.Errorf("не удалось найти Java %d: %v", feature, err)
	}
	pkg := assets[0].Binary.Package
	archive := filepath.Join(RuntimeDir(), pkg.Name)
	if err := DownloadBig("java", Task{URL: pkg.Link, Path: archive, Size: pkg.Size}, progress); err != nil {
		return "", err
	}
	if progress != nil {
		progress("java-extract", 0, 1)
	}
	tmp := dir + ".tmp"
	os.RemoveAll(tmp)
	var err error
	if strings.HasSuffix(pkg.Name, ".zip") {
		err = unzip(archive, tmp)
	} else {
		err = untarGz(archive, tmp)
	}
	if err != nil {
		return "", fmt.Errorf("extract Java: %w", err)
	}
	os.RemoveAll(dir)
	if err := os.Rename(tmp, dir); err != nil {
		return "", err
	}
	os.Remove(archive)
	j := findJava(dir)
	if j == "" {
		return "", fmt.Errorf("java executable not found after extract")
	}
	return j, nil
}

func safeJoin(dest, name string) (string, error) {
	p := filepath.Join(dest, name)
	if !strings.HasPrefix(filepath.Clean(p), filepath.Clean(dest)+string(os.PathSeparator)) {
		return "", fmt.Errorf("illegal path in archive: %s", name)
	}
	return p, nil
}

func unzip(src, dest string) error {
	r, err := zip.OpenReader(src)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		p, err := safeJoin(dest, f.Name)
		if err != nil {
			return err
		}
		if f.FileInfo().IsDir() {
			os.MkdirAll(p, 0o755)
			continue
		}
		os.MkdirAll(filepath.Dir(p), 0o755)
		rc, err := f.Open()
		if err != nil {
			return err
		}
		out, err := os.OpenFile(p, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, f.Mode()|0o600)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(out, rc)
		out.Close()
		rc.Close()
		if err != nil {
			return err
		}
	}
	return nil
}

func untarGz(src, dest string) error {
	f, err := os.Open(src)
	if err != nil {
		return err
	}
	defer f.Close()
	gz, err := gzip.NewReader(f)
	if err != nil {
		return err
	}
	tr := tar.NewReader(gz)
	for {
		h, err := tr.Next()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}
		p, err := safeJoin(dest, h.Name)
		if err != nil {
			return err
		}
		switch h.Typeflag {
		case tar.TypeDir:
			os.MkdirAll(p, 0o755)
		case tar.TypeReg:
			os.MkdirAll(filepath.Dir(p), 0o755)
			out, err := os.OpenFile(p, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, os.FileMode(h.Mode)|0o600)
			if err != nil {
				return err
			}
			_, err = io.Copy(out, tr)
			out.Close()
			if err != nil {
				return err
			}
		case tar.TypeSymlink:
			os.MkdirAll(filepath.Dir(p), 0o755)
			os.Symlink(h.Linkname, p)
		}
	}
}
