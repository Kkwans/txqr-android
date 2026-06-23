// Package mobile provides gomobile bindings for txqr decoder.
package mobile

import (
	"encoding/base64"
	"fmt"
	"math/rand"
	"strings"

	fountain "github.com/google/gofountain"
)

// Decoder implements txqr protocol decoder for gomobile.
type Decoder struct {
	chunkLen   int
	codec      fountain.Codec
	fd         fountain.Decoder
	completed  bool
	total      int
	cache      map[string]struct{}
	dataBytes  []byte
	frameCount int
}

// NewDecoder creates a new txqr decoder.
func NewDecoder() *Decoder {
	return &Decoder{
		cache: make(map[string]struct{}),
	}
}

// DecodeChunk feeds a QR frame string to the decoder.
func (d *Decoder) DecodeChunk(chunk string) error {
	if err := d.validate(chunk); err != nil {
		return err
	}

	idx := strings.IndexByte(chunk, '|')
	header := chunk[:idx]

	if d.isCached(header) {
		return nil
	}
	d.frameCount++

	var (
		blockCode       int64
		chunkLen, total int
	)
	_, err := fmt.Sscanf(header, "%d/%d/%d", &blockCode, &chunkLen, &total)
	if err != nil {
		return fmt.Errorf("invalid header: %v (%s)", err, header)
	}

	payload := chunk[idx+1:]
	lubyBlock := fountain.LTBlock{
		BlockCode: blockCode,
		Data:      []byte(payload),
	}

	if d.fd == nil {
		d.total = total
		d.chunkLen = chunkLen
		numChunks := numberOfChunks(d.total, d.chunkLen)
		d.codec = fountain.NewLubyCodec(numChunks, rand.New(fountain.NewMersenneTwister(200)), solitonDistribution(numChunks))
		d.fd = d.codec.NewDecoder(total)
	}
	d.completed = d.fd.AddBlocks([]fountain.LTBlock{lubyBlock})

	if d.completed {
		decoded := d.fd.Decode()
		decodedStr := string(decoded)
		// txqr-gif: file -> base64.StdEncoding -> fountain codes
		// 解码后应为 base64 字符串，需要 base64 解码得到原始文件
		raw, err := base64.StdEncoding.DecodeString(decodedStr)
		if err != nil {
			raw, err = base64.RawStdEncoding.DecodeString(decodedStr)
		}
		if err != nil {
			// base64 解码失败，返回原始数据
			d.dataBytes = decoded
		} else {
			d.dataBytes = raw
		}
	}

	return nil
}

// IsCompleted returns true when decoding is done.
func (d *Decoder) IsCompleted() bool { return d.completed }

// DataBytes returns the decoded file bytes.
func (d *Decoder) DataBytes() []byte { return d.dataBytes }

// Progress returns decoding progress (0-100).
// During decoding: estimates based on received frames vs estimated total (with 1.4x redundancy).
// Completed: returns 100.
func (d *Decoder) Progress() int {
	if d.completed {
		return 100
	}
	if d.total == 0 || d.chunkLen == 0 {
		return 0
	}
	minChunks := numberOfChunks(d.total, d.chunkLen)
	if minChunks == 0 {
		return 0
	}
	// Use 1.3x as estimated total (fountain codes typically need ~1.1-1.5x)
	estimatedTotal := minChunks * 13 / 10
	if estimatedTotal < minChunks {
		estimatedTotal = minChunks
	}
	p := d.frameCount * 100 / estimatedTotal
	if p > 95 {
		p = 95
	}
	return p
}

// TotalSize returns original data size in bytes.
func (d *Decoder) TotalSize() int { return d.total }

// ChunkLen returns the chunk length in bytes.
func (d *Decoder) ChunkLen() int { return d.chunkLen }

// TotalFrames returns the real total frames (data size / chunk length).
func (d *Decoder) TotalFrames() int {
	if d.chunkLen == 0 {
		return 0
	}
	return numberOfChunks(d.total, d.chunkLen)
}

// UniqueFrames returns count of unique frames received.
func (d *Decoder) UniqueFrames() int { return d.frameCount }

// Reset clears the decoder state.
func (d *Decoder) Reset() {
	d.fd = nil
	d.completed = false
	d.chunkLen = 0
	d.total = 0
	d.cache = map[string]struct{}{}
	d.codec = nil
	d.dataBytes = nil
	d.frameCount = 0
}

func (d *Decoder) validate(chunk string) error {
	if chunk == "" || len(chunk) < 4 {
		return fmt.Errorf("invalid frame: too short")
	}
	if !strings.Contains(chunk, "|") {
		return fmt.Errorf("invalid frame: missing separator")
	}
	return nil
}

func (d *Decoder) isCached(header string) bool {
	if _, ok := d.cache[header]; ok {
		return true
	}
	d.cache[header] = struct{}{}
	return false
}

func numberOfChunks(length, chunkLen int) int {
	n := length / chunkLen
	if length%chunkLen > 0 {
		n++
	}
	return n
}

func solitonDistribution(n int) []float64 {
	cdf := make([]float64, n+1)
	cdf[1] = 1 / float64(n)
	for i := 2; i < len(cdf); i++ {
		cdf[i] = cdf[i-1] + (1 / (float64(i) * float64(i-1)))
	}
	return cdf
}
