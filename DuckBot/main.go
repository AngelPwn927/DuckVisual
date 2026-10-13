// Duck Bot: accepts Telegram Stars donations for Duck Visual.
//
// It posts a "support the creator" message with Stars buttons to the channel,
// opens the payment window, confirms payments and says thanks.
// Settings come from config.json next to the binary (see config.example.json).
package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

type Config struct {
	Token   string `json:"token"`   // from @BotFather, never share it
	Channel string `json:"channel"` // e.g. "@duckvisual"
	AdminID int64  `json:"adminId"` // your Telegram user id: gets payment notices and admin commands
	Amounts []int  `json:"amounts"` // quick buttons, in Stars
	Cover   string `json:"cover"`   // picture for the channel post (optional)
	Contact string `json:"contact"` // where people write about payments, e.g. "t.me/duckvisual"
	APIURL  string `json:"apiUrl"`  // only for tests; default https://api.telegram.org
}

func loadConfig(path string) (*Config, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("нет файла %s: скопируй config.example.json в config.json и впиши токен", path)
	}
	c := &Config{}
	if err := json.Unmarshal(b, c); err != nil {
		return nil, fmt.Errorf("ошибка в %s: %w", path, err)
	}
	if t := os.Getenv("DUCKBOT_TOKEN"); t != "" {
		c.Token = t
	}
	if c.Token == "" || strings.Contains(c.Token, "ВСТАВЬ") {
		return nil, errors.New("в config.json не указан token (возьми его у @BotFather)")
	}
	if len(c.Amounts) == 0 {
		c.Amounts = []int{50, 100, 250}
	}
	if c.APIURL == "" {
		c.APIURL = "https://api.telegram.org"
	}
	if c.Contact == "" {
		c.Contact = "t.me/duckvisual"
	}
	return c, nil
}

/* ---------------- Telegram API ---------------- */

type Bot struct {
	cfg      *Config
	http     *http.Client
	username string
	dir      string // where donations.jsonl is written

	mu            sync.Mutex
	waitingAmount map[int64]bool // users who pressed "своя сумма"
}

type apiResp struct {
	OK          bool            `json:"ok"`
	Result      json.RawMessage `json:"result"`
	Description string          `json:"description"`
}

func (b *Bot) call(method string, params any, out any) error {
	body, _ := json.Marshal(params)
	resp, err := b.http.Post(b.cfg.APIURL+"/bot"+b.cfg.Token+"/"+method, "application/json", bytes.NewReader(body))
	if err != nil {
		return err
	}
	return decode(resp, method, out)
}

func decode(resp *http.Response, method string, out any) error {
	defer resp.Body.Close()
	var r apiResp
	if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
		return fmt.Errorf("%s: %w", method, err)
	}
	if !r.OK {
		return fmt.Errorf("%s: %s", method, r.Description)
	}
	if out != nil {
		return json.Unmarshal(r.Result, out)
	}
	return nil
}

// sendPhotoFile uploads a local picture with a caption and buttons.
func (b *Bot) sendPhotoFile(chat any, path, caption string, markup any) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	w.WriteField("chat_id", fmt.Sprint(chat))
	w.WriteField("caption", caption)
	w.WriteField("parse_mode", "HTML")
	mk, _ := json.Marshal(markup)
	w.WriteField("reply_markup", string(mk))
	part, _ := w.CreateFormFile("photo", filepath.Base(path))
	if _, err := io.Copy(part, f); err != nil {
		return err
	}
	w.Close()
	resp, err := b.http.Post(b.cfg.APIURL+"/bot"+b.cfg.Token+"/sendPhoto", w.FormDataContentType(), &buf)
	if err != nil {
		return err
	}
	return decode(resp, "sendPhoto", nil)
}

type User struct {
	ID        int64  `json:"id"`
	FirstName string `json:"first_name"`
	Username  string `json:"username"`
}

type Message struct {
	MessageID         int64              `json:"message_id"`
	From              *User              `json:"from"`
	Chat              struct{ ID int64 } `json:"chat"`
	Text              string             `json:"text"`
	SuccessfulPayment *struct {
		Currency                string `json:"currency"`
		TotalAmount             int    `json:"total_amount"`
		InvoicePayload          string `json:"invoice_payload"`
		TelegramPaymentChargeID string `json:"telegram_payment_charge_id"`
	} `json:"successful_payment"`
}

type Update struct {
	UpdateID      int64    `json:"update_id"`
	Message       *Message `json:"message"`
	CallbackQuery *struct {
		ID      string   `json:"id"`
		From    User     `json:"from"`
		Data    string   `json:"data"`
		Message *Message `json:"message"`
	} `json:"callback_query"`
	PreCheckoutQuery *struct {
		ID             string `json:"id"`
		From           User   `json:"from"`
		Currency       string `json:"currency"`
		TotalAmount    int    `json:"total_amount"`
		InvoicePayload string `json:"invoice_payload"`
	} `json:"pre_checkout_query"`
}

type button struct {
	Text         string `json:"text"`
	URL          string `json:"url,omitempty"`
	CallbackData string `json:"callback_data,omitempty"`
}

type keyboard struct {
	InlineKeyboard [][]button `json:"inline_keyboard"`
}

/* ---------------- donations ---------------- */

const (
	minStars = 1
	maxStars = 10000
	payload  = "duck-donation"
)

func invoice(amount int) map[string]any {
	return map[string]any{
		"title":          "Поддержать Duck Visual",
		"description":    fmt.Sprintf("Добровольный донат %d ⭐ создателю Duck Visual и Duck Launcher. Ничего не покупается: это просто спасибо.", amount),
		"payload":        payload,
		"provider_token": "", // empty for Telegram Stars
		"currency":       "XTR",
		"prices":         []map[string]any{{"label": "Донат", "amount": amount}},
	}
}

// invoiceLink makes a link that opens the Stars payment window right away (works from a channel post).
func (b *Bot) invoiceLink(amount int) (string, error) {
	var link string
	err := b.call("createInvoiceLink", invoice(amount), &link)
	return link, err
}

func (b *Bot) sendInvoice(chat int64, amount int) error {
	p := invoice(amount)
	p["chat_id"] = chat
	return b.call("sendInvoice", p, nil)
}

func (b *Bot) send(chat any, text string, markup any) error {
	p := map[string]any{"chat_id": chat, "text": text, "parse_mode": "HTML", "disable_web_page_preview": true}
	if markup != nil {
		p["reply_markup"] = markup
	}
	return b.call("sendMessage", p, nil)
}

func (b *Bot) amountsKeyboard() keyboard {
	var row []button
	for _, a := range b.cfg.Amounts {
		row = append(row, button{Text: fmt.Sprintf("⭐ %d", a), CallbackData: "pay:" + strconv.Itoa(a)})
	}
	return keyboard{InlineKeyboard: [][]button{row, {{Text: "✏️ Своя сумма", CallbackData: "custom"}}}}
}

const postText = "🦆 <b>Поддержать Duck Visual</b>\n\n" +
	"Мод и лаунчер бесплатные и такими останутся.\n" +
	"Если хочешь поддержать разработку — нажми на кнопку ниже и выбери, сколько звёзд отправить. Любая сумма помогает!\n\n" +
	"Спасибо, что играешь с Duck Visual 💛"

// postToChannel publishes the support post: quick amounts open the payment window directly,
// "своя сумма" leads to the bot.
func (b *Bot) postToChannel() error {
	if b.cfg.Channel == "" {
		return errors.New("в config.json не указан channel")
	}
	var row []button
	for _, a := range b.cfg.Amounts {
		link, err := b.invoiceLink(a)
		if err != nil {
			return err
		}
		row = append(row, button{Text: fmt.Sprintf("⭐ %d", a), URL: link})
	}
	kb := keyboard{InlineKeyboard: [][]button{
		{{Text: "⭐ Поддержать создателя", URL: "https://t.me/" + b.username + "?start=donate"}},
		row,
	}}
	if b.cfg.Cover != "" {
		cover := b.cfg.Cover
		if !filepath.IsAbs(cover) {
			cover = filepath.Join(b.dir, cover)
		}
		if _, err := os.Stat(cover); err == nil {
			return b.sendPhotoFile(b.cfg.Channel, cover, postText, kb)
		}
		log.Printf("обложка %s не найдена, отправляю пост без картинки", cover)
	}
	return b.send(b.cfg.Channel, postText, kb)
}

type donation struct {
	Time     time.Time `json:"time"`
	UserID   int64     `json:"userId"`
	Name     string    `json:"name"`
	Username string    `json:"username,omitempty"`
	Stars    int       `json:"stars"`
	ChargeID string    `json:"chargeId"`
}

func (b *Bot) logDonation(d donation) {
	f, err := os.OpenFile(filepath.Join(b.dir, "donations.jsonl"), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600)
	if err != nil {
		log.Println("не могу записать донат:", err)
		return
	}
	defer f.Close()
	json.NewEncoder(f).Encode(d)
}

func (b *Bot) stats() (count, total int) {
	data, _ := os.ReadFile(filepath.Join(b.dir, "donations.jsonl"))
	for _, line := range strings.Split(string(data), "\n") {
		var d donation
		if json.Unmarshal([]byte(line), &d) == nil && d.Stars > 0 {
			count++
			total += d.Stars
		}
	}
	return
}

func name(u *User) string {
	if u == nil {
		return "кто-то"
	}
	if u.Username != "" {
		return "@" + u.Username
	}
	return u.FirstName
}

/* ---------------- update handling ---------------- */

func (b *Bot) handle(u Update) {
	switch {
	case u.PreCheckoutQuery != nil:
		q := u.PreCheckoutQuery
		// must answer within 10 seconds, otherwise the payment fails
		ok := q.Currency == "XTR" && q.InvoicePayload == payload && q.TotalAmount >= minStars && q.TotalAmount <= maxStars
		p := map[string]any{"pre_checkout_query_id": q.ID, "ok": ok}
		if !ok {
			p["error_message"] = "Этот платёж не получилось принять, попробуй ещё раз."
		}
		if err := b.call("answerPreCheckoutQuery", p, nil); err != nil {
			log.Println(err)
		}

	case u.CallbackQuery != nil:
		cq := u.CallbackQuery
		b.call("answerCallbackQuery", map[string]any{"callback_query_id": cq.ID}, nil)
		switch {
		case strings.HasPrefix(cq.Data, "pay:"):
			amount, _ := strconv.Atoi(strings.TrimPrefix(cq.Data, "pay:"))
			if amount >= minStars && amount <= maxStars {
				if err := b.sendInvoice(cq.From.ID, amount); err != nil {
					log.Println(err)
				}
			}
		case cq.Data == "custom":
			b.mu.Lock()
			b.waitingAmount[cq.From.ID] = true
			b.mu.Unlock()
			b.send(cq.From.ID, fmt.Sprintf("Напиши, сколько звёзд отправить — числом от %d до %d.", minStars, maxStars), nil)
		}

	case u.Message != nil && u.Message.SuccessfulPayment != nil:
		m := u.Message
		sp := m.SuccessfulPayment
		d := donation{Time: time.Now(), Stars: sp.TotalAmount, ChargeID: sp.TelegramPaymentChargeID}
		if m.From != nil {
			d.UserID, d.Name, d.Username = m.From.ID, m.From.FirstName, m.From.Username
		}
		b.logDonation(d)
		b.send(m.Chat.ID, fmt.Sprintf("Спасибо за %d ⭐! 💛\nТы помогаешь Duck Visual становиться лучше.", sp.TotalAmount), nil)
		if b.cfg.AdminID != 0 {
			b.send(b.cfg.AdminID, fmt.Sprintf("⭐ Новый донат: <b>%d</b> от %s (id %d)\nchargeId: <code>%s</code>",
				sp.TotalAmount, htmlEscape(name(m.From)), d.UserID, sp.TelegramPaymentChargeID), nil)
		}

	case u.Message != nil && u.Message.Text != "":
		b.handleText(u.Message)
	}
}

func (b *Bot) handleText(m *Message) {
	text := strings.TrimSpace(m.Text)
	cmd := strings.Fields(text)
	first := ""
	if len(cmd) > 0 {
		first = strings.ToLower(strings.Split(cmd[0], "@")[0])
	}
	isAdmin := m.From != nil && b.cfg.AdminID != 0 && m.From.ID == b.cfg.AdminID

	switch first {
	case "/start", "/donate":
		b.send(m.Chat.ID, "🦆 <b>Поддержать Duck Visual</b>\n\nМод и лаунчер бесплатные. Если хочешь сказать спасибо — выбери, сколько звёзд отправить:", b.amountsKeyboard())
		return
	case "/terms":
		b.send(m.Chat.ID, "Это добровольный донат создателю Duck Visual и Duck Launcher. За звёзды ничего не продаётся и не выдаётся: ни функций, ни привилегий. Мод и лаунчер остаются бесплатными для всех.", nil)
		return
	case "/paysupport":
		b.send(m.Chat.ID, "Если с оплатой что-то не так — напиши в "+htmlEscape(b.cfg.Contact)+" и приложи скриншот платежа. Разберёмся и при необходимости вернём звёзды.", nil)
		return
	case "/id":
		if m.From != nil {
			b.send(m.Chat.ID, fmt.Sprintf("Твой id: <code>%d</code>", m.From.ID), nil)
		}
		return
	case "/post":
		if isAdmin {
			if err := b.postToChannel(); err != nil {
				b.send(m.Chat.ID, "Не получилось опубликовать: "+htmlEscape(err.Error())+"\nПроверь, что бот — админ канала с правом публиковать сообщения.", nil)
			} else {
				b.send(m.Chat.ID, "Готово, пост в "+htmlEscape(b.cfg.Channel)+" опубликован. Закрепи его в канале.", nil)
			}
		}
		return
	case "/stats":
		if isAdmin {
			c, t := b.stats()
			b.send(m.Chat.ID, fmt.Sprintf("Донатов: %d\nВсего звёзд: %d ⭐", c, t), nil)
		}
		return
	case "/refund":
		if isAdmin {
			if len(cmd) != 3 {
				b.send(m.Chat.ID, "Формат: /refund <id пользователя> <chargeId>", nil)
				return
			}
			uid, _ := strconv.ParseInt(cmd[1], 10, 64)
			err := b.call("refundStarPayment", map[string]any{"user_id": uid, "telegram_payment_charge_id": cmd[2]}, nil)
			if err != nil {
				b.send(m.Chat.ID, "Возврат не прошёл: "+htmlEscape(err.Error()), nil)
			} else {
				b.send(m.Chat.ID, "Звёзды возвращены.", nil)
			}
		}
		return
	}

	// a number after "своя сумма"
	if m.From != nil {
		b.mu.Lock()
		waiting := b.waitingAmount[m.From.ID]
		b.mu.Unlock()
		if waiting {
			n, err := strconv.Atoi(text)
			if err != nil || n < minStars || n > maxStars {
				b.send(m.Chat.ID, fmt.Sprintf("Нужно число от %d до %d.", minStars, maxStars), nil)
				return
			}
			b.mu.Lock()
			delete(b.waitingAmount, m.From.ID)
			b.mu.Unlock()
			if err := b.sendInvoice(m.Chat.ID, n); err != nil {
				log.Println(err)
			}
		}
	}
}

func htmlEscape(s string) string {
	return strings.NewReplacer("&", "&amp;", "<", "&lt;", ">", "&gt;").Replace(s)
}

/* ---------------- main loop ---------------- */

func (b *Bot) run(stop <-chan struct{}) {
	var offset int64
	for {
		select {
		case <-stop:
			return
		default:
		}
		var updates []Update
		err := b.call("getUpdates", map[string]any{
			"offset": offset, "timeout": 30,
			"allowed_updates": []string{"message", "callback_query", "pre_checkout_query"},
		}, &updates)
		if err != nil {
			log.Println("getUpdates:", err)
			time.Sleep(3 * time.Second)
			continue
		}
		for _, u := range updates {
			offset = u.UpdateID + 1
			b.handle(u)
		}
	}
}

func newBot(cfg *Config, dir string) (*Bot, error) {
	b := &Bot{cfg: cfg, http: &http.Client{Timeout: 45 * time.Second}, dir: dir, waitingAmount: map[int64]bool{}}
	var me User
	if err := b.call("getMe", nil, &me); err != nil {
		return nil, fmt.Errorf("токен не подошёл: %w", err)
	}
	b.username = me.Username
	return b, nil
}

func main() {
	exe, _ := os.Executable()
	dir := filepath.Dir(exe)
	if _, err := os.Stat(filepath.Join(dir, "config.json")); err != nil {
		dir, _ = os.Getwd() // `go run .` keeps the binary in a temp folder
	}
	cfg, err := loadConfig(filepath.Join(dir, "config.json"))
	if err != nil {
		log.Fatal(err)
	}
	b, err := newBot(cfg, dir)
	if err != nil {
		log.Fatal(err)
	}
	log.Printf("бот @%s запущен. Напиши ему /post, чтобы опубликовать пост в %s", b.username, cfg.Channel)
	b.run(make(chan struct{}))
}
