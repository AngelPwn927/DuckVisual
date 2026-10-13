package core

import (
	"bufio"
	"encoding/binary"
	"fmt"
	"io"
)

// A tiny uncompressed NBT reader/writer, enough to edit servers.dat while keeping
// everything else the game stored there.

const (
	tagEnd byte = iota
	tagByte
	tagShort
	tagInt
	tagLong
	tagFloat
	tagDouble
	tagByteArray
	tagString
	tagList
	tagCompound
	tagIntArray
	tagLongArray
)

// nbtField is a named tag inside a compound (order is kept).
type nbtField struct {
	Name  string
	Type  byte
	Value any
}

// nbtList is the value of a list tag.
type nbtList struct {
	Elem  byte
	Items []any
}

// nbtCompound is the value of a compound tag.
type nbtCompound []nbtField

func (c nbtCompound) get(name string) (nbtField, bool) {
	for _, f := range c {
		if f.Name == name {
			return f, true
		}
	}
	return nbtField{}, false
}

func (c nbtCompound) str(name string) string {
	if f, ok := c.get(name); ok && f.Type == tagString {
		return f.Value.(string)
	}
	return ""
}

func readNBT(r io.Reader) (nbtCompound, error) {
	br := bufio.NewReader(r)
	var t byte
	if err := binary.Read(br, binary.BigEndian, &t); err != nil {
		return nil, err
	}
	if t != tagCompound {
		return nil, fmt.Errorf("nbt: root is not a compound")
	}
	if _, err := nbtReadString(br); err != nil {
		return nil, err
	}
	v, err := nbtReadPayload(br, tagCompound, 0)
	if err != nil {
		return nil, err
	}
	return v.(nbtCompound), nil
}

func writeNBT(w io.Writer, root nbtCompound) error {
	bw := bufio.NewWriter(w)
	bw.WriteByte(tagCompound)
	nbtWriteString(bw, "")
	if err := nbtWritePayload(bw, tagCompound, root); err != nil {
		return err
	}
	return bw.Flush()
}

func nbtReadString(r io.Reader) (string, error) {
	var n uint16
	if err := binary.Read(r, binary.BigEndian, &n); err != nil {
		return "", err
	}
	b := make([]byte, n)
	_, err := io.ReadFull(r, b)
	return string(b), err
}

func nbtWriteString(w io.Writer, s string) {
	binary.Write(w, binary.BigEndian, uint16(len(s)))
	io.WriteString(w, s)
}

func nbtReadPayload(r io.Reader, t byte, depth int) (any, error) {
	if depth > 64 {
		return nil, fmt.Errorf("nbt: too deep")
	}
	be := binary.BigEndian
	switch t {
	case tagByte:
		var v int8
		return v, binary.Read(r, be, &v)
	case tagShort:
		var v int16
		return v, binary.Read(r, be, &v)
	case tagInt:
		var v int32
		return v, binary.Read(r, be, &v)
	case tagLong:
		var v int64
		return v, binary.Read(r, be, &v)
	case tagFloat:
		var v float32
		return v, binary.Read(r, be, &v)
	case tagDouble:
		var v float64
		return v, binary.Read(r, be, &v)
	case tagString:
		return nbtReadString(r)
	case tagByteArray, tagIntArray, tagLongArray:
		var n int32
		if err := binary.Read(r, be, &n); err != nil {
			return nil, err
		}
		if n < 0 || n > 1<<24 {
			return nil, fmt.Errorf("nbt: bad array length")
		}
		switch t {
		case tagByteArray:
			v := make([]int8, n)
			return v, binary.Read(r, be, v)
		case tagIntArray:
			v := make([]int32, n)
			return v, binary.Read(r, be, v)
		default:
			v := make([]int64, n)
			return v, binary.Read(r, be, v)
		}
	case tagList:
		var elem byte
		var n int32
		if err := binary.Read(r, be, &elem); err != nil {
			return nil, err
		}
		if err := binary.Read(r, be, &n); err != nil {
			return nil, err
		}
		if n < 0 || n > 1<<20 {
			return nil, fmt.Errorf("nbt: bad list length")
		}
		l := nbtList{Elem: elem}
		for i := int32(0); i < n; i++ {
			v, err := nbtReadPayload(r, elem, depth+1)
			if err != nil {
				return nil, err
			}
			l.Items = append(l.Items, v)
		}
		return l, nil
	case tagCompound:
		var c nbtCompound
		for {
			var ft byte
			if err := binary.Read(r, be, &ft); err != nil {
				return nil, err
			}
			if ft == tagEnd {
				return c, nil
			}
			name, err := nbtReadString(r)
			if err != nil {
				return nil, err
			}
			v, err := nbtReadPayload(r, ft, depth+1)
			if err != nil {
				return nil, err
			}
			c = append(c, nbtField{name, ft, v})
		}
	}
	return nil, fmt.Errorf("nbt: unknown tag %d", t)
}

func nbtWritePayload(w io.Writer, t byte, v any) error {
	be := binary.BigEndian
	switch t {
	case tagString:
		nbtWriteString(w, v.(string))
		return nil
	case tagByteArray:
		a := v.([]int8)
		binary.Write(w, be, int32(len(a)))
		return binary.Write(w, be, a)
	case tagIntArray:
		a := v.([]int32)
		binary.Write(w, be, int32(len(a)))
		return binary.Write(w, be, a)
	case tagLongArray:
		a := v.([]int64)
		binary.Write(w, be, int32(len(a)))
		return binary.Write(w, be, a)
	case tagList:
		l := v.(nbtList)
		if len(l.Items) == 0 {
			l.Elem = tagEnd
		}
		binary.Write(w, be, l.Elem)
		binary.Write(w, be, int32(len(l.Items)))
		for _, it := range l.Items {
			if err := nbtWritePayload(w, l.Elem, it); err != nil {
				return err
			}
		}
		return nil
	case tagCompound:
		for _, f := range v.(nbtCompound) {
			binary.Write(w, be, f.Type)
			nbtWriteString(w, f.Name)
			if err := nbtWritePayload(w, f.Type, f.Value); err != nil {
				return err
			}
		}
		return binary.Write(w, be, tagEnd)
	default:
		return binary.Write(w, be, v)
	}
}
