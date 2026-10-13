package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
)

// fakeTelegram records every Bot API call the bot makes.
type fakeTelegram struct {
	mu    sync.Mutex
	calls []call
}

type call struct {
	Method string
	Params map[string]any
	Raw    string
}

func (f *fakeTelegram) handler(w http.ResponseWriter, r *http.Request) {
	method := r.URL.Path[strings.LastIndex(r.URL.Path, "/")+1:]
	body, _ := io.ReadAll(r.Body)
	c := call{Method: method, Raw: string(body)}
	json.Unmarshal(body, &c.Params)
	f.mu.Lock()
	f.calls = append(f.calls, c)
	f.mu.Unlock()
	switch method {
	case "getMe":
		io.WriteString(w, `{"ok":true,"result":{"id":1,"first_name":"Duck","username":"duckvisual_bot"}}`)
	case "createInvoiceLink":
		io.WriteString(w, `{"ok":true,"result":"https://t.me/$inv`+jsonNum(c.Params["prices"])+`"}`)
	default:
		io.WriteString(w, `{"ok":true,"result":true}`)
	}
}

func jsonNum(prices any) string {
	p := prices.([]any)[0].(map[string]any)
	b, _ := json.Marshal(p["amount"])
	return string(b)
}

func (f *fakeTelegram) take() []call {
	f.mu.Lock()
	defer f.mu.Unlock()
	c := f.calls
	f.calls = nil
	return c
}

func setup(t *testing.T) (*Bot, *fakeTelegram) {
	t.Helper()
	f := &fakeTelegram{}
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	t.Cleanup(srv.Close)
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "cover.png"), []byte("\x89PNG fake"), 0o644)
	cfg := &Config{Token: "T", Channel: "@duckvisual", AdminID: 42, Amounts: []int{50, 100, 250}, Cover: "cover.png", Contact: "t.me/duckvisual", APIURL: srv.URL}
	b, err := newBot(cfg, dir)
	if err != nil {
		t.Fatal(err)
	}
	f.take()
	return b, f
}

func upd(js string) Update {
	var u Update
	if err := json.Unmarshal([]byte(js), &u); err != nil {
		panic(err)
	}
	return u
}

func TestStartShowsAmounts(t *testing.T) {
	b, f := setup(t)
	b.handle(upd(`{"update_id":1,"message":{"message_id":1,"from":{"id":7,"first_name":"Pl"},"chat":{"id":7},"text":"/start donate"}}`))
	c := f.take()
	if len(c) != 1 || c[0].Method != "sendMessage" || !strings.Contains(c[0].Raw, "pay:100") || !strings.Contains(c[0].Raw, "custom") {
		t.Fatalf("unexpected calls: %+v", c)
	}
}

func TestButtonSendsStarsInvoice(t *testing.T) {
	b, f := setup(t)
	b.handle(upd(`{"update_id":2,"callback_query":{"id":"q","from":{"id":7,"first_name":"Pl"},"data":"pay:100"}}`))
	c := f.take()
	if len(c) != 2 || c[1].Method != "sendInvoice" {
		t.Fatalf("unexpected calls: %+v", c)
	}
	p := c[1].Params
	if p["currency"] != "XTR" || p["provider_token"] != "" || p["payload"] != payload || jsonNum(p["prices"]) != "100" {
		t.Fatalf("bad invoice: %v", p)
	}
}

func TestPreCheckout(t *testing.T) {
	b, f := setup(t)
	b.handle(upd(`{"update_id":3,"pre_checkout_query":{"id":"pc","from":{"id":7},"currency":"XTR","total_amount":100,"invoice_payload":"duck-donation"}}`))
	b.handle(upd(`{"update_id":4,"pre_checkout_query":{"id":"pc2","from":{"id":7},"currency":"XTR","total_amount":100,"invoice_payload":"other"}}`))
	c := f.take()
	if c[0].Params["ok"] != true || c[1].Params["ok"] != false {
		t.Fatalf("pre-checkout answers wrong: %+v", c)
	}
}

func TestPaymentThanksLogsAndNotifies(t *testing.T) {
	b, f := setup(t)
	b.handle(upd(`{"update_id":5,"message":{"message_id":9,"from":{"id":7,"first_name":"Pl","username":"pl"},"chat":{"id":7},
		"successful_payment":{"currency":"XTR","total_amount":250,"invoice_payload":"duck-donation","telegram_payment_charge_id":"ch1"}}}`))
	c := f.take()
	if len(c) != 2 || !strings.Contains(c[0].Raw, "250") || c[1].Params["chat_id"] != float64(42) || !strings.Contains(c[1].Raw, "ch1") {
		t.Fatalf("unexpected calls: %+v", c)
	}
	if n, total := b.stats(); n != 1 || total != 250 {
		t.Fatalf("stats %d %d", n, total)
	}
}

func TestCustomAmount(t *testing.T) {
	b, f := setup(t)
	b.handle(upd(`{"update_id":6,"callback_query":{"id":"q","from":{"id":7},"data":"custom"}}`))
	b.handle(upd(`{"update_id":7,"message":{"message_id":2,"from":{"id":7},"chat":{"id":7},"text":"abc"}}`))
	b.handle(upd(`{"update_id":8,"message":{"message_id":3,"from":{"id":7},"chat":{"id":7},"text":"777"}}`))
	c := f.take()
	last := c[len(c)-1]
	if last.Method != "sendInvoice" || jsonNum(last.Params["prices"]) != "777" {
		t.Fatalf("custom amount not invoiced: %+v", c)
	}
	// the next plain message must not create another invoice
	b.handle(upd(`{"update_id":9,"message":{"message_id":4,"from":{"id":7},"chat":{"id":7},"text":"5"}}`))
	if c := f.take(); len(c) != 0 {
		t.Fatalf("unexpected calls: %+v", c)
	}
}

func TestPostOnlyForAdmin(t *testing.T) {
	b, f := setup(t)
	b.handle(upd(`{"update_id":10,"message":{"message_id":5,"from":{"id":7},"chat":{"id":7},"text":"/post"}}`))
	if c := f.take(); len(c) != 0 {
		t.Fatalf("non-admin could post: %+v", c)
	}
	b.handle(upd(`{"update_id":11,"message":{"message_id":6,"from":{"id":42},"chat":{"id":42},"text":"/post"}}`))
	c := f.take()
	var methods []string
	for _, x := range c {
		methods = append(methods, x.Method)
	}
	if strings.Join(methods, ",") != "createInvoiceLink,createInvoiceLink,createInvoiceLink,sendPhoto,sendMessage" {
		t.Fatalf("unexpected calls: %v", methods)
	}
	photo := c[3].Raw
	for _, want := range []string{"@duckvisual", "https://t.me/duckvisual_bot?start=donate", "https://t.me/$inv250", "Поддержать создателя"} {
		if !strings.Contains(photo, want) {
			t.Fatalf("post misses %q", want)
		}
	}
}
