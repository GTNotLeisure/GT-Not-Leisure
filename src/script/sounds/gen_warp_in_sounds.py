"""
Generates the warp-in build animation sounds of GT-Not-Leisure from scratch: every sample is synthesized here with
numpy, nothing is read from other recordings. Output is 44100 Hz mono OGG/Vorbis, timed to StructureWarpInAnimation.

Usage: python gen_warp_in_sounds.py [preview_dir]
Writes into src/main/resources/assets/sciencenotleisure/sounds/warp_in; with preview_dir, also writes a full timeline
preview there. Needs numpy and soundfile.

  1. warp_in/tear.ogg        (0.82s) - Stage 1: Tesla arc ignition + glassy spatial tear + vacuum pull
  2. warp_in/rift.ogg        (1.48s) - Stage 2: Deep gravitational singularity drop + dark swirling void + reverse vacuum suck
  3. warp_in/collapse.ogg    (0.76s) - Stage 2->3: Implosion snap + piercing crystalline needle-star flare
  4. warp_in/materialize.ogg (2.00s) - Stage 3->4: Energy-lattice granular weave + alien sus2 crystal resonance peaking at 1.80s
  5. warp_in/snap.ogg        (1.90s) - Stage 5: Atomic phase-lock crack + deep sub-shockwave + roaring mist/air wave + noble tail
  6. warp_in/arc1..3.ogg     (0.24-0.30s) - Dry, snapping high-voltage plasma whips (3 variations)
"""

import os
import sys
import numpy as np
import soundfile as sf

SR = 44100
OUT_DIR = os.path.normpath(
    os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "..", "main", "resources", "assets", "sciencenotleisure", "sounds", "warp_in"))


def fft_bandpass(x, lo, hi, order=2.0):
    n = len(x)
    nfft = 1 << int(np.ceil(np.log2(max(2, n))))
    X = np.fft.rfft(x, nfft)
    f = np.fft.rfftfreq(nfft, 1.0 / SR)
    mask = np.ones_like(f)
    if lo is not None and lo > 0:
        mask /= np.sqrt(1.0 + (lo / np.maximum(f, 1e-3)) ** (2.0 * order))
        mask[0] = 0.0
    if hi is not None and hi < SR * 0.5:
        mask /= np.sqrt(1.0 + (f / hi) ** (2.0 * order))
    return np.fft.irfft(X * mask, nfft)[:n]


def tv_lowpass(x, cutoff_hz):
    n = len(x)
    y = np.zeros(n, dtype=np.float64)
    block = 32
    s1 = 0.0
    s2 = 0.0
    for i in range(0, n, block):
        end = min(n, i + block)
        fc = float(np.clip(cutoff_hz[i], 28.0, SR * 0.44))
        a = 1.0 - np.exp(-2.0 * np.pi * fc / SR)
        seg = x[i:end]
        for j in range(end - i):
            s1 += a * (seg[j] - s1)
            s2 += a * (s1 - s2)
            y[i + j] = s2
    return y


def tv_bandpass(x, center_hz, q=2.5):
    n = len(x)
    y = np.zeros(n, dtype=np.float64)
    block = 32
    lp = 0.0
    bp = 0.0
    damp = 1.0 / max(0.5, q)
    for i in range(0, n, block):
        end = min(n, i + block)
        fc = float(np.clip(center_hz[i], 35.0, SR * 0.36))
        f = min(0.85, 2.0 * np.sin(np.pi * fc / SR))
        seg = x[i:end]
        for j in range(end - i):
            hp = seg[j] - lp - damp * bp
            bp += f * hp
            lp += f * bp
            y[i + j] = bp
    return y


def comb_reverb(x, wet=0.28, decay=0.65, bright=5200.0):
    n = len(x)
    out = np.zeros(n, dtype=np.float64)
    delays_ms = [19.3, 31.7, 46.1, 61.9, 79.3, 103.7]
    gains = [0.54, 0.49, 0.45, 0.41, 0.37, 0.33]
    src = fft_bandpass(x, 110.0, bright, order=1.4)
    for d_ms, g in zip(delays_ms, gains):
        d = int(d_ms * SR / 1000.0)
        if d <= 0 or d >= n:
            continue
        buf = np.zeros(n, dtype=np.float64)
        fb = g * decay
        amp = 1.0
        shift = d
        while shift < n and amp > 0.008:
            buf[shift:] += src[: n - shift] * amp
            shift += d
            amp *= fb
        out += buf
    out /= len(delays_ms) * 0.56
    out = fft_bandpass(out, 85.0, bright, order=1.2)
    return x * (1.0 - wet * 0.30) + out * wet


def freq_sweep_osc(freq_array, phase0=0.0, wave="sine"):
    phase = phase0 + 2.0 * np.pi * np.cumsum(freq_array) / SR
    if wave == "sine":
        return np.sin(phase)
    if wave == "tri":
        return (2.0 / np.pi) * np.arcsin(np.sin(phase))
    if wave == "rich":
        return (
            np.sin(phase)
            + 0.42 * np.sin(2 * phase)
            + 0.24 * np.sin(3 * phase)
            + 0.14 * np.sin(4 * phase)
            + 0.07 * np.sin(5 * phase)
        ) * 0.62
    return np.sin(phase)


def fm_crystal(t, carrier_hz, mod_ratio=2.76, mod_index=1.8, env=None, rng=None):
    """Inharmonic glass/crystal FM shimmer (Protoss khala/pylon timbre, not plain organ)."""
    if rng is None:
        rng = np.random.default_rng(7)
    ph_c = rng.uniform(0, 2 * np.pi)
    ph_m = rng.uniform(0, 2 * np.pi)
    if np.ndim(carrier_hz) == 0:
        c_phase = ph_c + 2.0 * np.pi * carrier_hz * t
        m_phase = ph_m + 2.0 * np.pi * (carrier_hz * mod_ratio) * t
    else:
        c_phase = ph_c + 2.0 * np.pi * np.cumsum(carrier_hz) / SR
        m_phase = ph_m + 2.0 * np.pi * np.cumsum(carrier_hz * mod_ratio) / SR
    idx = mod_index * (0.35 + 0.65 * env) if env is not None else mod_index
    sig = np.sin(c_phase + idx * np.sin(m_phase))
    return sig if env is None else sig * env


def crackle_bursts(n, rng, rate_hz=45.0, env=None, lo=1800.0, hi=11500.0, pitch_lo=2400.0, pitch_hi=7200.0):
    if env is None:
        env = np.ones(n)
    impulses = np.zeros(n, dtype=np.float64)
    expected = int(rate_hz * (n / SR))
    positions = rng.integers(0, max(1, n - 300), size=max(1, expected))
    for pos in positions:
        if env[pos] < 0.04:
            continue
        dur = int(rng.integers(32, 180))
        end = min(n, pos + dur)
        seg_len = end - pos
        tau = np.linspace(0, 6.0, seg_len)
        burst = rng.standard_normal(seg_len) * np.exp(-tau)
        # Sharp electric micro-ping
        burst += 0.55 * np.sin(2 * np.pi * rng.uniform(pitch_lo, pitch_hi) * np.arange(seg_len) / SR) * np.exp(
            -tau * 1.4
        )
        impulses[pos:end] += burst * rng.uniform(0.30, 1.0) * env[pos]
    return fft_bandpass(impulses, lo, hi, order=2.0)


def granular_lattice(n, rng, env, freq_scale=1.0):
    """High-tech crystalline energy-lattice weaving sparkles (tiny resonant glass pings)."""
    out = np.zeros(n, dtype=np.float64)
    # Alien open-5th/sus2 pentatonic-ish partials (D, E, A, B, D, E, A)
    scale = np.array([587.33, 659.25, 880.0, 987.77, 1174.66, 1318.51, 1760.0, 2349.32]) * freq_scale
    count = int(115 * (n / SR))
    positions = rng.integers(0, max(1, n - 1800), size=count)
    for pos in positions:
        e = env[pos]
        if e < 0.05:
            continue
        dur = int(rng.integers(350, 1400))
        end = min(n, pos + dur)
        seg_len = end - pos
        tt = np.arange(seg_len) / SR
        f0 = float(rng.choice(scale)) * (0.96 + 0.14 * (pos / n))
        # Tiny glass-grain FM ping
        grain = np.sin(2 * np.pi * f0 * tt + 0.8 * np.sin(2 * np.pi * f0 * 2.41 * tt))
        win = (1.0 - np.exp(-tt / 0.0025)) * np.exp(-tt / (dur / SR * 0.35))
        out[pos:end] += grain * win * rng.uniform(0.18, 0.55) * e
    return fft_bandpass(out, 600.0, 9500.0, order=1.6)


def master(x, target_peak=0.80, fade_in_ms=3.0, fade_out_ms=35.0):
    x = fft_bandpass(x, 26.0, 18000.0, order=2.0)
    peak = np.max(np.abs(x)) + 1e-12
    # Mild soft-knee saturation for cinematic density without harsh clipping
    x = np.tanh((x / peak) * 1.25) / np.tanh(1.25)
    fi = min(len(x) // 4, max(1, int(fade_in_ms * SR / 1000.0)))
    fo = min(len(x) // 2, max(1, int(fade_out_ms * SR / 1000.0)))
    x[:fi] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(fi) / fi)
    x[-fo:] *= 0.5 + 0.5 * np.cos(np.pi * np.arange(fo) / fo)
    peak = np.max(np.abs(x)) + 1e-12
    x = x * (target_peak / peak)
    return x.astype(np.float32)


def make_tear():
    """Stage 1 (0.82s): Tesla arc ignition + spatial glass-rip + inward air-pull into the wormhole."""
    rng = np.random.default_rng(111)
    dur = 0.82
    n = int(dur * SR)
    t = np.arange(n) / SR
    u = t / dur

    # 1. Sharp high-voltage ignition crack at t=0.005s
    ign_env = np.exp(-t / 0.042)
    ign_crack = fft_bandpass(rng.standard_normal(n), 2400, 14000, 2.0) * ign_env * 1.25
    ign_zap = freq_sweep_osc(2600 * np.exp(-t * 22.0) + 640, wave="rich") * np.exp(-t / 0.065) * 0.45
    ign_zap = fft_bandpass(ign_zap, 800, 7500, 1.5)

    # 2. Rising crystalline psionic FM resonance (open 5th D4+A4+E5, mysterious/alien, NOT organ)
    sweep = 0.78 + 0.62 * (u ** 1.45)
    c_env = np.sin(np.pi * np.clip(u / 0.90, 0, 1)) ** 0.9 * (0.30 + 0.70 * u)
    crystal = (
        0.42 * fm_crystal(t, 293.66 * sweep, mod_ratio=2.41, mod_index=1.25, env=c_env, rng=rng)
        + 0.34 * fm_crystal(t, 440.00 * sweep, mod_ratio=1.99, mod_index=0.95, env=c_env, rng=rng)
        + 0.24 * fm_crystal(t, 659.25 * sweep, mod_ratio=2.76, mod_index=1.10, env=c_env, rng=rng)
    )
    crystal = fft_bandpass(crystal, 340, 6200, 1.4) * 0.58

    # 3. Escalating plasma arcs tearing space open
    arc_env = np.clip(u / 0.55, 0.25, 1.0) * np.exp(-np.maximum(0.0, t - 0.58) / 0.11)
    arcs = crackle_bursts(n, rng, rate_hz=76.0, env=arc_env, lo=2000, hi=12000) * 0.95

    # 4. Inward vacuum whoosh sucking air into t=0.58s
    pull_env = np.exp(-((t - 0.54) / 0.16) ** 2)
    pull_air = tv_bandpass(rng.standard_normal(n), 240.0 + 2100.0 * (u ** 2.0), q=2.0) * pull_env * 0.65
    pull_sub = freq_sweep_osc(105.0 - 58.0 * np.clip(t / 0.60, 0, 1), wave="sine") * pull_env * 0.42

    mix = ign_crack + ign_zap + crystal + arcs + pull_air + pull_sub
    mix = comb_reverb(mix, wet=0.22, decay=0.54, bright=7000.0)
    return master(mix, target_peak=0.78, fade_in_ms=1.5, fade_out_ms=55.0)


def make_rift():
    """Stage 2 (1.48s): Deep wormhole bass drop -> cavernous swirling dark void -> reverse vacuum pinch suck."""
    rng = np.random.default_rng(222)
    dur = 1.48
    n = int(dur * SR)
    t = np.arange(n) / SR

    # 1. Clean sci-fi gravity-well bass drop at t=0 (deep 36Hz singularity punch)
    drop_f = 36.0 + 118.0 * np.exp(-t / 0.088)
    drop_env = (1.0 - np.exp(-t / 0.005)) * np.exp(-t / 0.30)
    bass_drop = (freq_sweep_osc(drop_f, wave="sine") + 0.25 * freq_sweep_osc(drop_f * 2.0, wave="tri")) * drop_env * 1.10

    # Air implosion tear right at rift open
    rip_env = (1.0 - np.exp(-t / 0.006)) * np.exp(-t / 0.16)
    tear_rip = tv_bandpass(rng.standard_normal(n), 3000.0 * np.exp(-t * 7.5) + 220.0, q=1.5) * rip_env * 0.90

    # 2. Dark cavernous gravitational singularity rumble (much deeper & more atmospheric, less mid-hum)
    # Ducks out smoothly right around 1.12s so collapse hits with huge dynamic contrast!
    void_env = np.clip(t / 0.14, 0, 1) * (1.0 - np.clip((t - 1.06) / 0.22, 0, 1))
    lfo = np.sin(2 * np.pi * 2.7 * t) * 3.2 + np.sin(2 * np.pi * 6.1 * t) * 1.4
    pinch_u = np.clip((t - 0.80) / 0.38, 0, 1)
    pinch_bend = 1.0 + 0.85 * (pinch_u ** 2.3)

    void_sub = (
        0.56 * freq_sweep_osc((46.0 + lfo * 0.25) * pinch_bend, wave="sine")
        + 0.28 * freq_sweep_osc((92.0 + lfo * 0.55) * pinch_bend, wave="tri")
        + 0.14 * freq_sweep_osc((138.0 - lfo * 0.6) * pinch_bend, wave="sine")
    ) * void_env * 0.65

    # Swirling hollow spatial wind (two moving formant bands)
    wind1 = tv_bandpass(rng.standard_normal(n), 190.0 + 140.0 * np.sin(2 * np.pi * 1.8 * t) + 1100.0 * (pinch_u ** 2.1), q=3.0)
    wind2 = tv_bandpass(rng.standard_normal(n), 520.0 + 260.0 * np.cos(2 * np.pi * 2.6 * t) + 2200.0 * (pinch_u ** 2.4), q=3.8)
    swirl = (wind1 * 0.60 + wind2 * 0.34) * void_env

    # 3. Corona rim electric arcs (crisp, dry)
    arc_env = np.clip(t / 0.06, 0, 1) * (1.0 - np.clip((t - 0.82) / 0.24, 0, 1))
    rim_arcs = crackle_bursts(n, rng, rate_hz=44.0, env=arc_env, lo=2200, hi=11500) * 0.68

    # 4. Dramatic reverse-suck vacuum intake converging to t=1.18s (feeds straight into collapse!)
    suck_env = np.exp(-((t - 1.08) / 0.14) ** 2) * (1.0 - np.clip((t - 1.15) / 0.06, 0, 1))
    suck_air = tv_bandpass(rng.standard_normal(n), 350.0 + 4200.0 * (pinch_u ** 2.2), q=1.9) * suck_env * 0.85
    suck_whine = fm_crystal(t, 280.0 + 1350.0 * (pinch_u ** 2.4), mod_ratio=2.15, mod_index=0.8, env=suck_env, rng=rng) * 0.32

    mix = bass_drop + tear_rip + void_sub * 0.85 + swirl * 0.85 + rim_arcs + suck_air + suck_whine
    mix = comb_reverb(mix, wet=0.26, decay=0.62, bright=3800.0)
    return master(mix, target_peak=0.78, fade_in_ms=2.0, fade_out_ms=80.0)


def make_collapse():
    """Stage 2->3 (0.76s): Brief 30ms vacuum implosion zip -> sharp piercing needle-star snap + glassy shimmer."""
    rng = np.random.default_rng(333)
    dur = 0.76
    n = int(dur * SR)
    t = np.arange(n) / SR

    hit_t = 0.038
    # Ultra-fast inward vacuum whip before impact
    pre_u = np.clip(t / hit_t, 0, 1)
    pre = tv_bandpass(rng.standard_normal(n), 500.0 + 5500.0 * (pre_u ** 2), q=2.2) * (pre_u ** 1.5) * (t < hit_t) * 0.80

    post = np.maximum(0.0, t - hit_t)
    active = (t >= hit_t).astype(np.float64)

    # 1. Razor-crisp needle-flash transient + tight sub-implosion punch
    punch_f = 44.0 + 190.0 * np.exp(-post / 0.032)
    punch = freq_sweep_osc(punch_f, wave="sine") * np.exp(-post / 0.11) * active * 0.82
    flash_crack = fft_bandpass(rng.standard_normal(n), 2800, 15500, 1.6) * np.exp(-post / 0.055) * active * 0.95

    # 2. Piercing laser-glass star-burst chirp (fast downward shimmer + crystal ring)
    chirp = freq_sweep_osc(3200.0 * np.exp(-post / 0.045) + 880.0, wave="sine") * np.exp(-post / 0.16) * active * 0.38
    ring_env = (1.0 - np.exp(-post / 0.003)) * np.exp(-post / 0.26) * active
    crystal_ring = (
        0.38 * fm_crystal(post, 587.33, mod_ratio=2.41, mod_index=1.35, env=ring_env, rng=rng)
        + 0.32 * fm_crystal(post, 880.00, mod_ratio=1.99, mod_index=1.10, env=ring_env, rng=rng)
        + 0.24 * fm_crystal(post, 1318.51, mod_ratio=2.76, mod_index=0.90, env=ring_env, rng=rng)
    )
    crystal_ring = fft_bandpass(crystal_ring, 450, 9500, 1.4) * 0.72

    mix = pre + punch + flash_crack + chirp + crystal_ring
    mix = comb_reverb(mix, wet=0.30, decay=0.60, bright=8800.0)
    return master(mix, target_peak=0.82, fade_in_ms=1.5, fade_out_ms=70.0)


def make_materialize():
    """Stage 3->4 (2.00s, peaks at 1.80s): Granular energy-lattice weaving + alien crystal resonance + white-hot surge."""
    rng = np.random.default_rng(444)
    dur = 2.00
    peak_t = 1.80
    n = int(dur * SR)
    t = np.arange(n) / SR
    u = np.clip(t / peak_t, 0.0, 1.0)
    tail = np.where(t <= peak_t, 1.0, np.exp(-(t - peak_t) / 0.065))

    # 1. Alien Protoss crystalline resonance (suspended D-A-E open intervals, FM glass texture, NOT organ chord!)
    glide = 1.0 + 0.14 * (u ** 1.75)
    c_env = np.clip(t / 0.16, 0, 1) * (0.28 + 0.72 * (u ** 1.35)) * tail
    shimmer_lfo = 0.82 + 0.18 * np.sin(2 * np.pi * (6.5 + 8.0 * u) * t)
    crystal_bed = (
        0.34 * fm_crystal(t, 146.83 * glide, mod_ratio=2.0, mod_index=0.65, env=c_env, rng=rng)
        + 0.32 * fm_crystal(t, 293.66 * glide, mod_ratio=2.41, mod_index=0.95, env=c_env, rng=rng)
        + 0.28 * fm_crystal(t, 440.00 * glide, mod_ratio=1.99, mod_index=0.85, env=c_env, rng=rng)
        + 0.22 * fm_crystal(t, 659.25 * glide, mod_ratio=2.76, mod_index=1.05, env=c_env, rng=rng)
    ) * shimmer_lfo
    # Sweep lowpass open as the structure weaves from wireframe -> solid ice crystal -> incandescent peak
    crystal_bed = tv_lowpass(crystal_bed, 580.0 + 6200.0 * (u ** 1.8)) * 0.62

    # 2. Rapid crystalline energy-lattice granular weaving sparkles (signature sci-fi "assembling" texture!)
    lattice_env = np.clip(t / 0.10, 0, 1) * (0.45 + 0.55 * u) * tail
    lattice = granular_lattice(n, rng, lattice_env, freq_scale=1.0) * 0.72

    # 3. High-voltage plasma weave crackles crawling along wireframe
    arcs = crackle_bursts(n, rng, rate_hz=58.0, env=lattice_env, lo=2400, hi=12500) * 0.62

    # 4. Final 0.55s incandescent charge surge (t=1.25..1.80s): rising energy-core whine + sub tension + bright air
    surge_u = np.clip((t - 1.18) / (peak_t - 1.18), 0.0, 1.0)
    surge_env = (surge_u ** 1.75) * tail
    surge_whine = (
        0.30 * freq_sweep_osc(587.33 * (1.0 + 0.38 * surge_u), wave="sine")
        + 0.26 * freq_sweep_osc(880.00 * (1.0 + 0.38 * surge_u), wave="sine")
        + 0.18 * freq_sweep_osc(1318.5 * (1.0 + 0.38 * surge_u), wave="sine")
    ) * surge_env
    surge_sub = freq_sweep_osc(55.0 + 40.0 * surge_u, wave="sine") * surge_env * 0.36
    surge_air = tv_bandpass(rng.standard_normal(n), 850.0 + 6200.0 * surge_u, q=1.6) * surge_env * 0.58

    mix = crystal_bed + lattice + arcs + surge_whine * 0.55 + surge_sub + surge_air
    mix = comb_reverb(mix, wet=0.28, decay=0.60, bright=8200.0)
    return master(mix, target_peak=0.80, fade_in_ms=10.0, fade_out_ms=75.0)


def make_snap():
    """Stage 5 (1.90s): Atomic phase-lock crack + deep visceral sub-shockwave + billowing mist roar + noble Protoss tail."""
    rng = np.random.default_rng(555)
    dur = 1.90
    n = int(dur * SR)
    t = np.arange(n) / SR

    # 1. Instantaneous atomic phase-lock crack (sharp metallic/crystal snap)
    crack = fft_bandpass(rng.standard_normal(n), 2600, 16000, 1.4) * np.exp(-t / 0.020) * 1.35
    crystal_lock = (
        0.48 * fm_crystal(t, 1174.66, mod_ratio=2.41, mod_index=1.6, env=np.exp(-t / 0.055), rng=rng)
        + 0.42 * fm_crystal(t, 1760.00, mod_ratio=1.99, mod_index=1.3, env=np.exp(-t / 0.048), rng=rng)
    ) * 0.72

    # 2. Deep cinematic shockwave sub-boom (112Hz -> 32Hz, warm & weighty)
    boom_f = 32.0 + 88.0 * np.exp(-t / 0.072)
    boom_env = (1.0 - np.exp(-t / 0.003)) * np.exp(-t / 0.36)
    sub_boom = (
        1.00 * freq_sweep_osc(boom_f, wave="sine")
        + 0.30 * freq_sweep_osc(boom_f * 2.0, wave="tri") * np.exp(-t / 0.16)
    ) * boom_env * 1.05

    # 3. Massive expanding 3D billowing mist wall + transparent air-refraction wave
    whoosh_env = (1.0 - np.exp(-t / 0.022)) * np.exp(-t / 0.56)
    whoosh_fc = 190.0 + 3200.0 * np.exp(-t / 0.22)
    mist_whoosh = tv_bandpass(rng.standard_normal(n), whoosh_fc, q=1.15) * whoosh_env * 1.15
    mist_low = fft_bandpass(rng.standard_normal(n), 48, 210, 1.8) * (1.0 - np.exp(-t / 0.018)) * np.exp(-t / 0.48) * 0.65

    # 4. Noble, understated Protoss completion crystal shimmer tail (open sus2 D-A-E-A, decays smoothly)
    tail_env = (1.0 - np.exp(-t / 0.012)) * np.exp(-t / 0.68)
    noble_tail = (
        0.34 * fm_crystal(t, 293.66, mod_ratio=2.0, mod_index=0.45, env=tail_env, rng=rng)
        + 0.32 * fm_crystal(t, 440.00, mod_ratio=2.41, mod_index=0.50, env=tail_env, rng=rng)
        + 0.24 * fm_crystal(t, 659.25, mod_ratio=1.99, mod_index=0.45, env=tail_env, rng=rng)
        + 0.18 * fm_crystal(t, 880.00, mod_ratio=2.76, mod_index=0.35, env=tail_env, rng=rng)
    ) * 0.52

    mix = crack * 1.15 + crystal_lock * 1.15 + sub_boom * 1.25 + mist_whoosh * 1.15 + mist_low * 1.15 + noble_tail * 0.95
    mix = comb_reverb(mix, wet=0.28, decay=0.68, bright=6400.0)
    return master(mix, target_peak=0.90, fade_in_ms=1.0, fade_out_ms=130.0)


def make_arc(variant_seed):
    """Crisp dry high-voltage fractal lightning arc whip-crackle (~0.24-0.30s)."""
    rng = np.random.default_rng(700 + variant_seed * 41)
    dur = 0.23 + 0.03 * variant_seed
    n = int(dur * SR)
    t = np.arange(n) / SR

    env = np.exp(-t / (0.040 + 0.010 * variant_seed))
    snap = fft_bandpass(rng.standard_normal(n), 2600, 14500, 2.0) * env * 1.05

    f_start = rng.uniform(2400, 3900)
    f_end = rng.uniform(480, 880)
    zap = freq_sweep_osc(f_end + (f_start - f_end) * np.exp(-t / 0.028), wave="rich") * np.exp(-t / 0.048) * 0.38
    zap = fft_bandpass(zap, 1100, 9000, 1.5)

    sparks = crackle_bursts(n, rng, rate_hz=92.0, env=np.exp(-t / 0.095), lo=2800, hi=13500) * 0.78

    mix = snap + zap + sparks
    mix = comb_reverb(mix, wet=0.12, decay=0.35, bright=9000.0)
    return master(mix, target_peak=0.72, fade_in_ms=1.0, fade_out_ms=25.0)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None

    clips = {
        "tear.ogg": make_tear(),
        "rift.ogg": make_rift(),
        "collapse.ogg": make_collapse(),
        "materialize.ogg": make_materialize(),
        "snap.ogg": make_snap(),
        "arc1.ogg": make_arc(0),
        "arc2.ogg": make_arc(1),
        "arc3.ogg": make_arc(2),
    }

    for name, audio in clips.items():
        path = os.path.join(OUT_DIR, name)
        sf.write(path, audio, SR, format="OGG", subtype="VORBIS")
        info = sf.info(path)
        readback, _ = sf.read(path)
        rms = float(np.sqrt(np.mean(readback ** 2)))
        peak = float(np.max(np.abs(readback)))
        dc = float(np.abs(np.mean(readback)))
        size_kb = os.path.getsize(path) / 1024.0
        print(
            f"{name:16s} {size_kb:6.1f} KB | sr={info.samplerate} ch={info.channels} "
            f"dur={info.duration:.2f}s | peak={peak:.2f} rms={rms:.2f} dc={dc:.4f}"
        )

    if preview_dir is None:
        return
    os.makedirs(preview_dir, exist_ok=True)
    total_n = int(5.9 * SR)
    full = np.zeros(total_n, dtype=np.float64)

    def place(audio, start_sec, gain=1.0):
        idx = int(start_sec * SR)
        end = min(total_n, idx + len(audio))
        full[idx:end] += audio[: end - idx] * gain

    place(clips["tear.ogg"], 0.05, 0.88)
    place(clips["arc1.ogg"], 0.25, 0.36)
    place(clips["rift.ogg"], 0.60, 0.95)
    place(clips["arc2.ogg"], 0.95, 0.34)
    place(clips["arc3.ogg"], 1.32, 0.32)
    place(clips["collapse.ogg"], 1.70, 0.94)
    place(clips["materialize.ogg"], 2.00, 0.94)
    place(clips["arc1.ogg"], 2.40, 0.30)
    place(clips["arc2.ogg"], 2.90, 0.32)
    place(clips["arc3.ogg"], 3.35, 0.34)
    place(clips["snap.ogg"], 3.80, 1.00)

    preview_path = os.path.join(preview_dir, "warp_in_full_preview.ogg")
    sf.write(preview_path, np.clip(full, -0.96, 0.96).astype(np.float32), SR, format="OGG", subtype="VORBIS")
    print(f"Full timeline preview written to: {preview_path} (peak={np.max(np.abs(full)):.2f})")


if __name__ == "__main__":
    main()
