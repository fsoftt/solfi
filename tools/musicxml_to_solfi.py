#!/usr/bin/env python3
"""Convert a MusicXML score (.musicxml / .xml / compressed .mxl) into a Solfi ensemble piece.

Every part becomes one practisable voice with the notation string described in CONTENT.md.
Pitches are kept as WRITTEN for the part (what the player reads); `transpose` is the semitone
distance written -> sounding (B-flat trumpet -2, glockenspiel +24...).

    python3 tools/musicxml_to_solfi.py score.mxl --id intro-ngl > piece.json
    python3 tools/musicxml_to_solfi.py score.mxl --id intro-ngl --into composeApp/src/commonMain/composeResources/files/content/ngl.json

Limits (the script stops with an error when it meets them): one voice per part, no tuplets, no grace notes,
no time-signature changes; key changes are not supported either. Repeats are not expanded.
"""
import argparse, json, re, sys, zipfile
import xml.etree.ElementTree as ET

TICKS_PER_QUARTER = 24
DUR_CODES = {96: "w", 144: "w.", 168: "w..", 48: "h", 72: "h.", 84: "h..", 24: "q", 36: "q.", 42: "q..",
             12: "8", 18: "8.", 21: "8..", 6: "16", 9: "16."}
FIFTHS_MAJOR = {0: "C", 1: "G", 2: "D", 3: "A", 4: "E", 5: "B", 6: "F#", 7: "C#",
                -1: "F", -2: "Bb", -3: "Eb", -4: "Ab", -5: "Db", -6: "Gb", -7: "Cb"}
FIFTHS_MINOR = {0: "Am", 1: "Em", 2: "Bm", 3: "F#m", 4: "C#m", 5: "G#m", 6: "D#m", 7: "A#m",
                -1: "Dm", -2: "Gm", -3: "Cm", -4: "Fm", -5: "Bbm", -6: "Ebm", -7: "Abm"}
# MusicXML instrument names (any language MuseScore writes) -> Solfi instrument code
INSTRUMENTS = [
    (r"tromp|trump|tpt", "TRUMPET"), (r"tromb", "TROMBONE"), (r"tuba|tba", "TUBA"),
    (r"glock", "GLOCKENSPIEL"), (r"trompa|horn|corno", "FRENCH_HORN"), (r"clarin", "CLARINET"),
    (r"flaut|flute", "FLUTE"), (r"sax", "SAXOPHONE"), (r"viol[ií]n|violin", "VIOLIN"),
    (r"chelo|cello", "CELLO"), (r"fagot|bassoon", "BASSOON"), (r"guitar", "GUITAR"), (r"piano", "PIANO"),
]


def fail(msg):
    sys.exit("error: " + msg)


def load(path):
    if path.endswith(".mxl"):
        with zipfile.ZipFile(path) as z:
            container = ET.fromstring(z.read("META-INF/container.xml"))
            root_file = next(container.iter("{urn:oasis:names:tc:opendocument:xmlns:container}rootfile"), None)
            if root_file is None:
                root_file = next(e for e in container.iter() if e.tag.endswith("rootfile"))
            return ET.fromstring(z.read(root_file.get("full-path")))
    return ET.parse(path).getroot()


def instrument_code(name):
    low = name.lower()
    for pattern, code in INSTRUMENTS:
        if re.search(pattern, low):
            return code
    return "OTHER"


def voice_number(name):
    m = re.search(r"(\d+)\s*$", name.strip())
    return int(m.group(1)) if m else 1


def pitch_text(p):
    alter = int(float(p.findtext("alter") or 0))
    acc = {-2: "bb", -1: "b", 0: "", 1: "#", 2: "##"}[alter]
    return p.findtext("step") + acc + p.findtext("octave")


def convert_part(part, meta):
    divisions = None
    clef = key = time = None
    transpose = 0
    bars = []
    for m in part.findall("measure"):
        for a in m.findall("attributes"):
            if a.findtext("divisions"):
                divisions = int(a.findtext("divisions"))
            k = a.find("key")
            if k is not None:
                fifths = int(k.findtext("fifths"))
                new_key = (FIFTHS_MINOR if k.findtext("mode") == "minor" else FIFTHS_MAJOR)[fifths]
                if key is not None and new_key != key:
                    fail(f"key change in measure {m.get('number')} is not supported")
                key = new_key
            t = a.find("time")
            if t is not None:
                new_time = f"{t.findtext('beats')}/{t.findtext('beat-type')}"
                if time is not None and new_time != time:
                    fail(f"time signature change in measure {m.get('number')} is not supported")
                time = new_time
            c = a.find("clef")
            if c is not None:
                sign = c.findtext("sign")
                if sign not in ("G", "F"):
                    fail(f"clef {sign} is not supported (only treble and bass)")
                clef = "treble" if sign == "G" else "bass"
            tr = a.find("transpose")
            if tr is not None:
                transpose = int(tr.findtext("chromatic") or 0) + 12 * int(tr.findtext("octave-change") or 0)
        num, den = map(int, time.split("/"))
        measure_ticks = num * 96 // den
        tokens, ticks = [], 0
        for n in m.findall("note"):
            if n.find("grace") is not None:
                fail(f"grace notes are not supported (measure {m.get('number')})")
            if n.find("time-modification") is not None:
                fail(f"tuplets are not supported (measure {m.get('number')})")
            if (n.findtext("voice") or "1") != "1":
                fail(f"several voices in one part are not supported (measure {m.get('number')})")
            dur = int(n.findtext("duration")) * TICKS_PER_QUARTER // divisions
            if n.find("chord") is not None:
                prev = tokens.pop()
                head, d = prev.rsplit(":", 1)
                inner = head[1:-1].split() if head.startswith("[") else [head]
                inner.append(pitch_text(n.find("pitch")))
                tokens.append("[" + " ".join(inner) + "]:" + d)
                continue
            code = DUR_CODES.get(dur)
            if code is None:
                fail(f"duration of {dur} ticks in measure {m.get('number')} cannot be written")
            if n.find("rest") is not None:
                tokens.append("r:" + code)
            else:
                tie = any(t.get("type") == "start" for t in n.findall("tie"))
                tokens.append(pitch_text(n.find("pitch")) + ":" + code + ("~" if tie else ""))
            ticks += dur
        if ticks != measure_ticks:
            fail(f"measure {m.get('number')} of {meta['name']} lasts {ticks} ticks, expected {measure_ticks}")
        bars.append(" ".join(tokens))
    return {**meta, "clef": clef, "key": key, "transpose": transpose, "time": time, "notes": " | ".join(bars)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("score")
    ap.add_argument("--id", required=True, help="piece id, e.g. intro-ngl")
    ap.add_argument("--title", help="defaults to <work-title>")
    ap.add_argument("--tempo", type=int, help="quarter notes per minute; defaults to the first <sound tempo>")
    ap.add_argument("--into", help="add (or replace) the piece in this NGL book JSON file")
    ap.add_argument("--keep-trailing-rests", action="store_true",
                    help="keep the final measures that are silent in every part (dropped by default)")
    args = ap.parse_args()

    root = load(args.score)
    names = {sp.get("id"): (sp.findtext("part-name") or sp.get("id")).strip() for sp in root.iter("score-part")}
    tempo = args.tempo or round(float(next((s.get("tempo") for s in root.iter("sound") if s.get("tempo")), 80)))
    parts = []
    for p in root.findall("part"):
        name = names[p.get("id")]
        parts.append(convert_part(p, {"id": p.get("id").lower(), "name": name,
                                      "instrument": instrument_code(name), "voice": voice_number(name)}))
    if not args.keep_trailing_rests:
        bars = [pt["notes"].split(" | ") for pt in parts]
        last = max(max((i for i, m in enumerate(b) if not all(t.startswith("r:") for t in m.split())), default=0) for b in bars)
        for pt, b in zip(parts, bars):
            pt["notes"] = " | ".join(b[:last + 1])
    times = {pt.pop("time") for pt in parts}
    if len(times) != 1:
        fail(f"parts disagree on the time signature: {times}")
    piece = {"id": args.id, "title": args.title or root.findtext("work/work-title") or args.id,
             "time": times.pop(), "tempo": tempo, "parts": parts}

    if args.into:
        try:
            with open(args.into, encoding="utf-8") as f:
                book = json.load(f)
        except FileNotFoundError:
            book = {"pieces": []}
        book["pieces"] = [x for x in book["pieces"] if x["id"] != piece["id"]] + [piece]
        with open(args.into, "w", encoding="utf-8") as f:
            json.dump(book, f, ensure_ascii=False, indent=2)
            f.write("\n")
    else:
        json.dump(piece, sys.stdout, ensure_ascii=False, indent=2)
        print()


if __name__ == "__main__":
    main()
