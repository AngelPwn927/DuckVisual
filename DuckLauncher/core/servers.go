package core

import (
	"bufio"
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// Server is a featured server: it is added to the in-game multiplayer list of every
// instance and shown on the "Серверы" page of the launcher.
type Server struct {
	Name  string `json:"name"`
	Host  string `json:"host"`
	Port  int    `json:"port"`
	About string `json:"about"`
}

func (s Server) Address() string {
	if s.Port == 0 || s.Port == 25565 {
		return s.Host
	}
	return s.Host + ":" + strconv.Itoa(s.Port)
}

// Servers are the launcher's featured servers.
var Servers = []Server{
	{Name: "Duck Server", Host: "n1.pg19.su", Port: 25795, About: "Сервер Duck Launcher"},
}

// EnsureServers puts the featured servers at the top of the instance's servers.dat,
// keeping every server the player added.
func EnsureServers(gameDir string) error {
	path := filepath.Join(gameDir, "servers.dat")
	var root nbtCompound
	if f, err := os.Open(path); err == nil {
		root, err = readNBT(f)
		f.Close()
		if err != nil {
			root = nil // broken file: the game would ignore it as well
		}
	}
	var list nbtList
	idx := -1
	for i, f := range root {
		if f.Name == "servers" && f.Type == tagList {
			list = f.Value.(nbtList)
			idx = i
		}
	}
	if list.Elem != tagCompound {
		list = nbtList{Elem: tagCompound}
	}
	var featured, rest []any
	for _, s := range Servers {
		entry := nbtCompound{{"name", tagString, s.Name}, {"ip", tagString, s.Address()}}
		// keep the icon and the resource pack choice the game saved for our server
		for _, it := range list.Items {
			c, _ := it.(nbtCompound)
			if strings.EqualFold(c.str("ip"), s.Address()) {
				for _, f := range c {
					if f.Name != "name" && f.Name != "ip" {
						entry = append(entry, f)
					}
				}
				break
			}
		}
		featured = append(featured, entry)
	}
	for _, it := range list.Items {
		c, _ := it.(nbtCompound)
		dup := false
		for _, s := range Servers {
			if strings.EqualFold(c.str("ip"), s.Address()) {
				dup = true
			}
		}
		if !dup {
			rest = append(rest, it)
		}
	}
	list.Items = append(featured, rest...)
	if idx >= 0 {
		root[idx].Value = list
	} else {
		root = append(root, nbtField{"servers", tagList, list})
	}
	var buf bytes.Buffer
	if err := writeNBT(&buf, root); err != nil {
		return err
	}
	os.MkdirAll(gameDir, 0o755)
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, buf.Bytes(), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

// ServerStatus is the result of a Server List Ping.
type ServerStatus struct {
	Online     bool   `json:"online"`
	Players    int    `json:"players"`
	MaxPlayers int    `json:"maxPlayers"`
	Version    string `json:"version"`
	MOTD       string `json:"motd"` // with § colour codes
	Favicon    string `json:"favicon"`
	PingMs     int64  `json:"ping"`
	Error      string `json:"error,omitempty"`
}

func writeVarInt(w io.ByteWriter, v int32) {
	u := uint32(v)
	for {
		if u&^0x7F == 0 {
			w.WriteByte(byte(u))
			return
		}
		w.WriteByte(byte(u&0x7F | 0x80))
		u >>= 7
	}
}

func readVarInt(r io.ByteReader) (int32, error) {
	var v uint32
	for i := 0; i < 5; i++ {
		b, err := r.ReadByte()
		if err != nil {
			return 0, err
		}
		v |= uint32(b&0x7F) << (7 * i)
		if b&0x80 == 0 {
			return int32(v), nil
		}
	}
	return 0, fmt.Errorf("varint too long")
}

func sendPacket(w io.Writer, id int32, body []byte) error {
	var p bytes.Buffer
	writeVarInt(&p, id)
	p.Write(body)
	var out bytes.Buffer
	writeVarInt(&out, int32(p.Len()))
	out.Write(p.Bytes())
	_, err := w.Write(out.Bytes())
	return err
}

// chatText flattens a chat component (string or JSON object) into §-coded text.
func chatText(raw json.RawMessage) string {
	var s string
	if json.Unmarshal(raw, &s) == nil {
		return s
	}
	var c struct {
		Text   string            `json:"text"`
		Color  string            `json:"color"`
		Bold   bool              `json:"bold"`
		Extra  []json.RawMessage `json:"extra"`
	}
	if json.Unmarshal(raw, &c) != nil {
		return ""
	}
	codes := map[string]string{"black": "0", "dark_blue": "1", "dark_green": "2", "dark_aqua": "3", "dark_red": "4",
		"dark_purple": "5", "gold": "6", "gray": "7", "dark_gray": "8", "blue": "9", "green": "a", "aqua": "b",
		"red": "c", "light_purple": "d", "yellow": "e", "white": "f"}
	var b strings.Builder
	if code, ok := codes[c.Color]; ok {
		b.WriteString("§" + code)
	} else if strings.HasPrefix(c.Color, "#") && len(c.Color) == 7 {
		b.WriteString("§#" + c.Color[1:])
	}
	if c.Bold {
		b.WriteString("§l")
	}
	b.WriteString(c.Text)
	for _, e := range c.Extra {
		b.WriteString("§r" + chatText(e))
	}
	return b.String()
}

// PingServer asks a server for its MOTD and player count.
func PingServer(s Server) ServerStatus {
	port := s.Port
	if port == 0 {
		port = 25565
	}
	addr := net.JoinHostPort(s.Host, strconv.Itoa(port))
	conn, err := net.DialTimeout("tcp", addr, 5*time.Second)
	if err != nil {
		return ServerStatus{Error: "сервер не отвечает"}
	}
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(6 * time.Second))

	var hs bytes.Buffer
	writeVarInt(&hs, 767) // protocol version; servers answer the status request with any value
	writeVarInt(&hs, int32(len(s.Host)))
	hs.WriteString(s.Host)
	binary.Write(&hs, binary.BigEndian, uint16(port))
	writeVarInt(&hs, 1) // next state: status
	if err := sendPacket(conn, 0, hs.Bytes()); err != nil {
		return ServerStatus{Error: err.Error()}
	}
	start := time.Now()
	sendPacket(conn, 0, nil)
	r := bufio.NewReader(conn)
	if _, err := readVarInt(r); err != nil { // packet length
		return ServerStatus{Error: "нет ответа"}
	}
	if id, err := readVarInt(r); err != nil || id != 0 {
		return ServerStatus{Error: "неверный ответ"}
	}
	n, err := readVarInt(r)
	if err != nil || n < 0 || n > 1<<21 {
		return ServerStatus{Error: "неверный ответ"}
	}
	body := make([]byte, n)
	if _, err := io.ReadFull(r, body); err != nil {
		return ServerStatus{Error: "неверный ответ"}
	}
	ping := time.Since(start).Milliseconds()
	var resp struct {
		Version struct {
			Name string `json:"name"`
		} `json:"version"`
		Players struct {
			Max    int `json:"max"`
			Online int `json:"online"`
		} `json:"players"`
		Description json.RawMessage `json:"description"`
		Favicon     string          `json:"favicon"`
	}
	if err := json.Unmarshal(body, &resp); err != nil {
		return ServerStatus{Error: "неверный ответ"}
	}
	// a real round trip for the latency
	var pb bytes.Buffer
	binary.Write(&pb, binary.BigEndian, time.Now().UnixMilli())
	t := time.Now()
	if sendPacket(conn, 1, pb.Bytes()) == nil {
		if _, err := readVarInt(r); err == nil {
			ping = time.Since(t).Milliseconds()
		}
	}
	fav := resp.Favicon
	if !strings.HasPrefix(fav, "data:image/png;base64,") {
		fav = ""
	}
	return ServerStatus{Online: true, Players: resp.Players.Online, MaxPlayers: resp.Players.Max,
		Version: resp.Version.Name, MOTD: chatText(resp.Description), Favicon: fav, PingMs: ping}
}
