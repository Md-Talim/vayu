package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"sync/atomic"
	"time"
)

type counters struct {
	requests atomic.Int64
	bytes    atomic.Int64
	first    atomic.Int64
	last     atomic.Int64
}

type statsResponse struct {
	Requests          int64   `json:"requests"`
	Bytes             int64   `json:"bytes"`
	FirstRequestAt    string  `json:"firstRequestAt,omitempty"`
	LastRequestAt     string  `json:"lastRequestAt,omitempty"`
	ElapsedSeconds    float64 `json:"elapsedSeconds"`
	RequestsPerSecond float64 `json:"requestsPerSecond"`
}

func main() {
	port := envInt("PORT", 9090)
	delay := time.Duration(envInt("RECEIVER_DELAY_MS", 0)) * time.Millisecond
	status := envInt("RECEIVER_STATUS", http.StatusOK)

	if status < 100 || status > 599 {
		log.Fatalf("RECEIVER_STATUS must be a valid HTTP status, got %d", status)
	}

	var c counters

	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok\n"))
	})
	mux.HandleFunc("/stats", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(snapshot(&c))
	})
	mux.HandleFunc("/webhook", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}

		n, err := io.Copy(io.Discard, r.Body)
		if err != nil {
			http.Error(w, "could not read body", http.StatusBadRequest)
			return
		}

		now := time.Now().UnixNano()
		c.requests.Add(1)
		c.bytes.Add(n)
		c.first.CompareAndSwap(0, now)
		c.last.Store(now)

		if delay > 0 {
			time.Sleep(delay)
		}

		w.WriteHeader(status)
		_, _ = w.Write([]byte("ok\n"))
	})

	server := &http.Server{
		Addr:              fmt.Sprintf(":%d", port),
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	log.Printf("benchmark receiver listening on :%d status=%d delay=%s", port, status, delay)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}

func snapshot(c *counters) statsResponse {
	requests := c.requests.Load()
	first := c.first.Load()
	last := c.last.Load()

	response := statsResponse{
		Requests: requests,
		Bytes:    c.bytes.Load(),
	}

	if first == 0 || last == 0 {
		return response
	}

	firstTime := time.Unix(0, first).UTC()
	lastTime := time.Unix(0, last).UTC()
	elapsed := lastTime.Sub(firstTime).Seconds()

	response.FirstRequestAt = firstTime.Format(time.RFC3339Nano)
	response.LastRequestAt = lastTime.Format(time.RFC3339Nano)
	response.ElapsedSeconds = elapsed

	if elapsed > 0 {
		response.RequestsPerSecond = float64(requests) / elapsed
	}

	return response
}

func envInt(name string, fallback int) int {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		log.Fatalf("%s must be an integer, got %q", name, value)
	}

	return parsed
}
