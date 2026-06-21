// Package mobile provides gomobile bindings for txqr decoder.
package mobile

import txqr "github.com/divan/txqr"

// Decoder wraps txqr.Decoder for gomobile.
type Decoder struct {
	inner *txqr.Decoder
}

// NewDecoder creates a new decoder.
func NewDecoder() *Decoder {
	return &Decoder{inner: txqr.NewDecoder()}
}

// DecodeChunk feeds a QR frame to the decoder.
func (d *Decoder) DecodeChunk(chunk string) error {
	return d.inner.DecodeChunk(chunk)
}

// IsCompleted returns true when decoding is done.
func (d *Decoder) IsCompleted() bool {
	return d.inner.IsCompleted()
}

// DataBytes returns the decoded file bytes.
func (d *Decoder) DataBytes() []byte {
	return d.inner.DataBytes()
}

// Progress returns decoding progress (0-100).
func (d *Decoder) Progress() int {
	return d.inner.Progress()
}

// TotalSize returns original data size in bytes.
func (d *Decoder) TotalSize() int {
	return d.inner.TotalSize()
}

// UniqueFrames returns count of unique frames received.
func (d *Decoder) UniqueFrames() int {
	return d.inner.UniqueFrames()
}

// Reset clears the decoder state.
func (d *Decoder) Reset() {
	d.inner.Reset()
}
