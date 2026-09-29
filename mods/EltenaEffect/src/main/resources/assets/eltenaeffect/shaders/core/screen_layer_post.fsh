#version 150

uniform sampler2D DiffuseSampler;
uniform int LayerType;
uniform float Strength;
uniform float BlurRadius;
uniform float Progress;
uniform float LayerProgress;
uniform float FocusLoss;
uniform float PressurePeak;
uniform float Aftershock;
uniform int SampleCount;
uniform float Frequency;
uniform float Speed;
uniform int EdgeOnly;
uniform int DirectionMode;
uniform vec4 TintColor;
uniform float NoiseStrength;
uniform float Wobble;
uniform float BlurScale;
uniform float RefractionStrength;
uniform float Desaturation;
uniform float Smear;
uniform float Pulse;
uniform int BurstCount;
uniform float BurstLength;
uniform float BurstSharpness;
uniform float CenterBias;
uniform float EdgeBias;
uniform float BurstChromaticOffset;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

const int MAX_SAMPLES = 24;
const vec2 SCREEN_CENTER = vec2(0.5, 0.5);

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec2 clampUv(vec2 uv) {
    return clamp(uv, vec2(0.001), vec2(0.999));
}

vec2 perpendicular(vec2 value) {
    return vec2(-value.y, value.x);
}

vec2 safeNormalize(vec2 value, vec2 fallbackValue) {
    float len = length(value);
    if (len <= 0.00001) {
        return fallbackValue;
    }
    return value / len;
}

float hash12(vec2 value) {
    vec3 p3 = fract(vec3(value.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2d(vec2 value) {
    vec2 i = floor(value);
    vec2 f = fract(value);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec2 resolveDirection(vec2 radialDir) {
    if (DirectionMode == 1) {
        return -radialDir;
    }
    if (DirectionMode == 2) {
        return vec2(-1.0, 0.0);
    }
    if (DirectionMode == 3) {
        return vec2(1.0, 0.0);
    }
    if (DirectionMode == 4) {
        return vec2(0.0, -1.0);
    }
    if (DirectionMode == 5) {
        return vec2(0.0, 1.0);
    }
    if (DirectionMode == 6) {
        return safeNormalize(vec2(-0.75, 0.75), radialDir);
    }
    if (DirectionMode == 7) {
        return safeNormalize(vec2(0.75, 0.75), radialDir);
    }
    return radialDir;
}

vec3 sampleDirectionalBlur(
    vec2 uv,
    vec2 primaryDir,
    vec2 secondaryDir,
    float radius,
    int sampleCount,
    float secondaryScale
) {
    vec3 accum = texture(DiffuseSampler, clampUv(uv)).rgb * 1.30;
    float totalWeight = 1.30;

    for (int i = 0; i < MAX_SAMPLES; i++) {
        if (i >= sampleCount) {
            break;
        }

        float t = float(i + 1) / float(max(sampleCount, 1));
        float weight = 1.0 - (t * 0.72);
        vec2 primaryOffset = primaryDir * radius * t;
        vec2 secondaryOffset = secondaryDir * radius * secondaryScale * t;

        accum += texture(DiffuseSampler, clampUv(uv - primaryOffset - (secondaryOffset * 0.45))).rgb * weight;
        accum += texture(DiffuseSampler, clampUv(uv + (primaryOffset * 0.58) + secondaryOffset)).rgb * (weight * 0.62);
        totalWeight += weight + (weight * 0.62);
    }

    return accum / max(totalWeight, 0.0001);
}

vec3 applyRadialBlur(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    vec2 resolvedDir = resolveDirection(radialDir);
    vec2 tangent = perpendicular(resolvedDir);
    float radius = max(BlurRadius, 0.10) * (0.0010 + (dist * 0.0105)) * (0.35 + (Strength * 0.65) + (FocusLoss * 0.18));
    radius *= max(0.55, BlurScale);
    int sampleCount = clamp(SampleCount, 2, MAX_SAMPLES);
    vec3 blurred = sampleDirectionalBlur(uv, resolvedDir, tangent, radius, sampleCount, 0.18 + (PressurePeak * 0.08));
    float edgeMask = smoothstep(0.03, 0.82, dist);
    float mixFactor = saturate((Strength * 0.28) + (edgeMask * 0.18) + (FocusLoss * 0.12) + (Aftershock * 0.06));
    return mix(baseColor, blurred, mixFactor);
}

vec3 applySonicPressureBurst(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    float reach = 0.18 + (saturate(BurstLength) * 0.74);
    float angleNorm = fract((atan(radialDir.y, radialDir.x) / 6.2831853) + 1.0);
    float burstCount = float(max(BurstCount, 1));
    float sharp = max(BurstSharpness, 1.0);
    float cellPos = angleNorm * burstCount;
    float cellId = floor(cellPos);
    float cellFrac = fract(cellPos);
    float jitter = hash12(vec2(cellId, BurstLength * 17.0)) - 0.5;
    float lineCenter = 0.5 + (jitter * 0.28);
    float lineWidth = max(0.010, (0.19 / (1.0 + (sharp * 0.10))) + (abs(jitter) * 0.010));
    float slit = saturate(1.0 - (abs(cellFrac - lineCenter) / lineWidth));
    slit = pow(slit, 1.4 + (sharp * 0.08));

    float microCell = fract((angleNorm * burstCount * 2.0) + (jitter * 1.7));
    float microLine = saturate(1.0 - (abs(microCell - 0.5) / max(0.06, lineWidth * 0.85)));
    microLine = pow(microLine, 1.0 + (sharp * 0.04));

    float front = 0.05 + (LayerProgress * reach * 0.95);
    float frontWidth = max(0.018, 0.020 + ((1.0 / (sharp + 2.0)) * 1.6) + (CenterBias * 0.018));
    float trailWidth = 0.055 + ((1.0 - LayerProgress) * 0.08) + (EdgeBias * 0.024);
    float frontMask = saturate(1.0 - (abs(dist - front) / frontWidth));
    frontMask = pow(frontMask, 1.2 + (sharp * 0.03));
    float trailMask = saturate(1.0 - (max(0.0, front - dist) / max(trailWidth, 0.0001)));
    trailMask *= 1.0 - smoothstep(front + 0.02, reach + 0.10, dist);
    float centerCore = saturate(1.0 - (dist / max(0.05, 0.07 + (CenterBias * 0.10))));
    centerCore = pow(centerCore, 1.15 + (CenterBias * 1.8));
    float edgeLift = pow(saturate(dist / max(reach, 0.001)), 0.75 + (EdgeBias * 1.25))
        * (1.0 - smoothstep(reach * 0.96, reach + 0.08, dist));
    float fractureNoise = (hash12(vec2(cellId * 0.73, floor(dist * 52.0))) * 0.75) + 0.25;
    float burstMask = saturate(
        (slit * frontMask * (1.25 + (EdgeBias * 0.28)))
            + (slit * trailMask * fractureNoise * 0.95)
            + (microLine * frontMask * 0.42)
            + (centerCore * 0.22)
            + (edgeLift * 0.14)
    );
    float smearRadius = (0.0010 + (dist * 0.0105))
        * (0.60 + (Strength * 0.90) + (BlurScale * 0.40) + (EdgeBias * 0.18));
    smearRadius *= 0.70 + (burstMask * 1.20) + (LayerProgress * 0.25);
    int sampleCount = clamp(max(SampleCount, 10), 10, MAX_SAMPLES);
    vec3 accum = baseColor * (0.86 + (burstMask * 0.22));
    float totalWeight = 0.86 + (burstMask * 0.22);

    for (int i = 0; i < MAX_SAMPLES; i++) {
        if (i >= sampleCount) {
            break;
        }

        float t = float(i + 1) / float(max(sampleCount, 1));
        float lineWeight = pow(1.0 - t, 0.30) * (0.16 + (burstMask * 1.35));
        vec2 outwardOffset = radialDir * smearRadius * t * (0.90 + (LayerProgress * 0.55));
        vec2 slitDrift = perpendicular(radialDir) * smearRadius * jitter * 0.10 * t;

        accum += texture(DiffuseSampler, clampUv(uv - outwardOffset - slitDrift)).rgb * lineWeight;
        accum += texture(DiffuseSampler, clampUv(uv - (outwardOffset * 1.72) - (slitDrift * 1.6))).rgb
            * (lineWeight * 0.38);
        totalWeight += lineWeight + (lineWeight * 0.38);
    }

    vec3 streaked = accum / max(totalWeight, 0.0001);
    vec3 burstTint = mix(vec3(0.86, 0.93, 1.0), TintColor.rgb, max(TintColor.a, 0.22) * 0.40);
    float highlightMask = burstMask * (0.65 + (frontMask * 0.55));
    vec3 highlighted = streaked + (burstTint * highlightMask * (0.020 + (Strength * 0.016)));

    if (BurstChromaticOffset > 0.00001) {
        vec2 chromaShift = radialDir * BurstChromaticOffset * highlightMask * (0.24 + (frontMask * 0.66) + (dist * 0.25));
        vec3 shifted = vec3(
            texture(DiffuseSampler, clampUv(uv - chromaShift)).r,
            highlighted.g,
            texture(DiffuseSampler, clampUv(uv + (chromaShift * 0.85))).b
        );
        highlighted = mix(highlighted, shifted, saturate((BurstChromaticOffset * 36.0) + (highlightMask * 0.08)));
    }

    float mixFactor = saturate(
        (Strength * 0.14)
            + (burstMask * (0.86 + (PressurePeak * 0.18)))
            + (frontMask * 0.16)
            + (FocusLoss * 0.04)
    );
    return mix(baseColor, highlighted, mixFactor);
}

vec3 applyMotionBlurCore(vec2 uv, vec2 radialDir, float dist, vec3 baseColor, bool fakeMode) {
    vec2 resolvedDir = resolveDirection(radialDir);
    vec2 tangent = perpendicular(resolvedDir);
    float radius = (0.0010 + (dist * 0.0068)) * (0.45 + (Strength * 1.10) + (BlurScale * 0.45) + (Smear * 0.65));
    int sampleCount = clamp(fakeMode ? max(4, SampleCount) : max(3, SampleCount / 2), 3, MAX_SAMPLES);
    vec3 blurred = sampleDirectionalBlur(
        uv,
        resolvedDir,
        fakeMode ? tangent : vec2(0.0, 0.0),
        radius,
        sampleCount,
        fakeMode ? (0.25 + (Smear * 0.35)) : 0.0
    );
    float mixFactor = saturate((Strength * (fakeMode ? 0.76 : 0.62)) + (PressurePeak * 0.12) + (Smear * 0.22));
    return mix(baseColor, blurred, mixFactor);
}

vec3 applyPressureDistortion(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    vec2 tangent = perpendicular(radialDir);
    float frequency = max(Frequency, 0.01);
    float speed = max(Speed, 0.01);
    float strength = max(Strength, RefractionStrength);
    float front = exp(-pow((dist - (0.10 + (LayerProgress * 0.72))) * (4.2 + (frequency * 0.22)), 2.0));
    float wave = sin((dist * (14.0 + (frequency * 2.8))) - (Time * (2.0 + (speed * 5.8))) + (LayerProgress * 10.0));
    float shearing = cos((dot(uv, vec2(41.0, 27.0))) + (Time * (1.4 + (speed * 4.5))));
    float amplitude = strength * (0.0015 + (PressurePeak * 0.0016) + (Aftershock * 0.0012) + (Wobble * 0.0010));
    amplitude *= (wave * 0.50) + (front * 0.95);
    vec2 warpedUv = uv + (radialDir * amplitude) + (tangent * amplitude * 0.72 * shearing);
    vec3 warped = texture(DiffuseSampler, clampUv(warpedUv)).rgb;
    float mixFactor = saturate((Strength * 0.88) + (PressurePeak * 0.16));
    return mix(baseColor, warped, mixFactor);
}

vec3 applyShockwaveRefraction(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    vec2 tangent = perpendicular(radialDir);
    float ringFront = 0.08 + (LayerProgress * 0.84);
    float ring = exp(-pow((dist - ringFront) * (9.0 + (Frequency * 0.45)), 2.0));
    float squeeze = 1.0 - smoothstep(ringFront, ringFront + 0.12, dist);
    float amplitude = (0.0020 + (max(RefractionStrength, Strength) * 0.0030)) * ring;
    vec2 warpedUv = uv + (radialDir * amplitude * (1.2 + (PressurePeak * 0.3))) + (tangent * amplitude * 0.45 * sin(Time * (7.0 + Speed * 2.0)));
    vec3 refracted = texture(DiffuseSampler, clampUv(warpedUv)).rgb;
    vec3 compressed = sampleDirectionalBlur(uv, radialDir, tangent, amplitude * 18.0, clamp(SampleCount, 3, 10), 0.12);
    vec3 combined = mix(refracted, compressed, saturate((Strength * 0.18) + (squeeze * 0.18)));
    return mix(baseColor, combined, saturate((Strength * 0.80) + (ring * 0.20)));
}

vec3 applyHeatRefraction(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    float scale = 18.0 + (Frequency * 4.0);
    float wobbleNoise = noise2d((uv * vec2(scale, scale * 0.65)) + vec2(Time * (0.9 + Speed * 1.6), Time * 0.24));
    float wobbleWave = sin((uv.y * (26.0 + Frequency * 4.0)) + (Time * (2.0 + Speed * 3.0)));
    float amplitude = (0.0010 + (Strength * 0.0022) + (Wobble * 0.0018) + (RefractionStrength * 0.0018))
        * (0.65 + (wobbleNoise * 0.85))
        * (0.55 + (wobbleWave * 0.45));
    vec2 warpedUv = uv + vec2((wobbleNoise - 0.5) * amplitude, wobbleWave * amplitude * 0.85);
    vec3 refracted = texture(DiffuseSampler, clampUv(warpedUv)).rgb;
    float haze = saturate((Strength * 0.20) + (NoiseStrength * 0.12));
    vec3 warmed = mix(refracted, refracted + (TintColor.rgb * 0.06), haze * max(TintColor.a, 0.35));
    return mix(baseColor, warmed, saturate((Strength * 0.78) + (Wobble * 0.14)));
}

vec3 applyUnderwaterRefraction(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    float frequency = max(Frequency, 0.01);
    float wobbleX = sin((uv.y * (10.0 + frequency)) + (Time * (1.1 + Speed * 1.8)));
    float wobbleY = cos((uv.x * (8.0 + frequency * 0.7)) + (Time * (0.9 + Speed * 1.4)));
    float macro = sin((dist * (12.0 + frequency * 0.9)) - (Time * (0.7 + Speed)));
    float amplitude = (0.0016 + (Strength * 0.0022) + (RefractionStrength * 0.0020) + (Wobble * 0.0014))
        * (0.65 + (macro * 0.35));
    vec2 warpedUv = uv + vec2(wobbleX, wobbleY) * amplitude;
    vec3 refracted = texture(DiffuseSampler, clampUv(warpedUv)).rgb;
    vec3 cooled = mix(refracted, mix(refracted, TintColor.rgb, 0.18), max(TintColor.a, 0.25) * 0.55);
    return mix(baseColor, cooled, saturate((Strength * 0.84) + (Aftershock * 0.10)));
}

vec3 applyChromaticAberration(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    vec2 resolvedDir = resolveDirection(radialDir);
    float edgeMask = EdgeOnly == 1 ? smoothstep(0.06, 0.42, dist) : 1.0;
    float offset = Strength * edgeMask * (0.50 + (PressurePeak * 0.30) + (LayerProgress * 0.25)) * (0.42 + (dist * 1.08));
    vec2 shift = resolvedDir * offset;
    vec3 shifted = vec3(
        texture(DiffuseSampler, clampUv(uv + shift)).r,
        texture(DiffuseSampler, clampUv(uv + (shift * 0.15))).g,
        texture(DiffuseSampler, clampUv(uv - (shift * 0.82))).b
    );
    float mixFactor = saturate(Strength * 42.0) * edgeMask;
    return mix(baseColor, shifted, mixFactor);
}

vec3 applyColorGrade(vec3 baseColor) {
    float tintMix = saturate((Strength * 0.30) + (TintColor.a * 0.55));
    vec3 graded = mix(baseColor, baseColor * (0.92 + (TintColor.rgb * 0.18)), tintMix);
    graded = mix(graded, TintColor.rgb, tintMix * 0.16);
    return graded;
}

vec3 applyDesaturation(vec3 baseColor) {
    float amount = saturate((Strength * 0.72) + Desaturation);
    vec3 gray = vec3(luma(baseColor));
    vec3 desat = mix(baseColor, gray, amount);
    return mix(desat, desat * (0.96 + (TintColor.rgb * 0.04)), max(TintColor.a, 0.0) * 0.15);
}

vec3 applyWhiteout(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    vec3 blurred = sampleDirectionalBlur(
        uv,
        resolveDirection(radialDir),
        perpendicular(radialDir),
        (0.0012 + (dist * 0.0050)) * (0.65 + (BlurScale * 0.45) + (Strength * 0.85)),
        clamp(SampleCount, 3, 10),
        0.10
    );
    vec3 tint = mix(vec3(1.0), TintColor.rgb, max(TintColor.a, 0.0) * 0.45);
    vec3 washed = mix(blurred, tint, saturate((Strength * 0.32) + (TintColor.a * 0.45)));
    float mixFactor = saturate((Strength * 0.74) + (FocusLoss * 0.10));
    return mix(baseColor, washed, mixFactor);
}

vec3 applyDarkVignetteSoft(vec2 uv, float dist, vec3 baseColor) {
    float edge = smoothstep(0.24, 0.98, dist);
    float pulse = 0.70 + (sin(Time * (1.2 + Pulse * 1.8)) * 0.08);
    float darken = saturate((Strength * 0.62) + (PressurePeak * 0.16)) * edge * pulse;
    vec3 tinted = baseColor * (1.0 - darken);
    tinted = mix(tinted, tinted * (0.85 + (TintColor.rgb * 0.15)), max(TintColor.a, 0.15) * edge * 0.35);
    return tinted;
}

vec3 applyNoiseFog(vec2 uv, float dist, vec3 baseColor) {
    float scale = 18.0 + (Frequency * 5.0);
    float fogNoise = noise2d((uv * vec2(scale, scale * 0.74)) + vec2(Time * (0.22 + Speed * 0.25), Time * (0.12 + Speed * 0.18)));
    float pulseWave = 0.5 + (sin(Time * (1.3 + Pulse * 3.0)) * 0.5);
    float fog = saturate((Strength * 0.22) + (NoiseStrength * 0.52) + (pulseWave * 0.08));
    fog *= 0.55 + (smoothstep(0.10, 0.92, dist) * 0.25);
    vec3 tint = mix(vec3(0.94), TintColor.rgb, max(TintColor.a, 0.35));
    vec3 hazed = mix(baseColor, mix(baseColor, tint, 0.24 + (fogNoise * 0.18)), fog);
    return hazed;
}

vec3 applyScreenShakeWarp(vec2 uv, vec2 radialDir, vec3 baseColor) {
    vec2 resolvedDir = resolveDirection(radialDir);
    vec2 tangent = perpendicular(resolvedDir);
    float jitter = (noise2d((uv * vec2(28.0, 22.0)) + vec2(Time * (8.0 + Speed * 4.0), Time * 0.8)) - 0.5);
    float sway = sin((Time * (12.0 + Speed * 6.0)) + (uv.y * (18.0 + Frequency * 2.0)));
    float amplitude = (0.0008 + (Strength * 0.0018) + (Wobble * 0.0016) + (PressurePeak * 0.0008));
    vec2 warpedUv = uv + (resolvedDir * amplitude * sway) + (tangent * amplitude * jitter * 1.8);
    vec3 warped = texture(DiffuseSampler, clampUv(warpedUv)).rgb;
    return mix(baseColor, warped, saturate((Strength * 0.84) + (Wobble * 0.12)));
}

vec3 applyGhosting(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    vec2 resolvedDir = resolveDirection(radialDir);
    float ghostOffset = (0.0012 + (dist * 0.0042)) * (0.55 + (Strength * 0.95) + (Smear * 0.85));
    vec3 trailA = texture(DiffuseSampler, clampUv(uv - (resolvedDir * ghostOffset))).rgb;
    vec3 trailB = texture(DiffuseSampler, clampUv(uv + (resolvedDir * ghostOffset * 0.52))).rgb;
    vec3 trailC = texture(DiffuseSampler, clampUv(uv - (perpendicular(resolvedDir) * ghostOffset * 0.35))).rgb;
    vec3 ghosted = (baseColor * 0.56) + (trailA * 0.24) + (trailB * 0.12) + (trailC * 0.08);
    return mix(baseColor, ghosted, saturate((Strength * 0.88) + (Smear * 0.16)));
}

vec3 applyTimeWarp(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    vec2 resolvedDir = resolveDirection(radialDir);
    vec2 tangent = perpendicular(resolvedDir);
    float phase = sin((uv.x + uv.y) * (10.0 + Frequency * 1.4) - (Time * (2.0 + Speed * 4.0)) + (LayerProgress * 10.0));
    float eddy = noise2d((uv * vec2(14.0 + Frequency, 11.0 + Frequency * 0.7)) + vec2(Time * 0.6, -Time * 0.4)) - 0.5;
    float amplitude = (0.0010 + (Strength * 0.0022) + (RefractionStrength * 0.0016) + (Smear * 0.0012));
    vec2 warpedUv = uv + (resolvedDir * phase * amplitude * 1.4) + (tangent * eddy * amplitude * 1.8);
    vec3 warped = texture(DiffuseSampler, clampUv(warpedUv)).rgb;
    vec3 trailed = sampleDirectionalBlur(uv, resolvedDir, tangent, amplitude * 10.0, clamp(SampleCount, 3, 8), 0.16);
    return mix(warped, trailed, saturate((Strength * 0.24) + (Smear * 0.20)));
}

vec3 applyFractureDistortion(vec2 uv, vec2 radialDir, float dist, vec3 baseColor) {
    float fractureScale = 10.0 + (Frequency * 1.8);
    float cellA = noise2d((uv * fractureScale) + vec2(Time * 0.24, -Time * 0.15));
    float cellB = noise2d((uv * (fractureScale * 1.35)) - vec2(Time * 0.12, Time * 0.20));
    float crack = smoothstep(0.38, 0.78, abs(cellA - cellB) + (dist * 0.18));
    vec2 fractureDir = safeNormalize(vec2(cellA - 0.5, cellB - 0.5), radialDir);
    float amplitude = (0.0010 + (Strength * 0.0024) + (RefractionStrength * 0.0018)) * crack;
    vec2 warpedUv = uv + (fractureDir * amplitude * 1.6);
    vec3 fractured = texture(DiffuseSampler, clampUv(warpedUv)).rgb;
    fractured = mix(fractured, fractured * (0.52 + (TintColor.rgb * 0.18)), crack * 0.36);
    return mix(baseColor, fractured, saturate((Strength * 0.88) + (crack * 0.08)));
}

void main() {
    vec2 uv = texCoord;
    vec2 delta = uv - SCREEN_CENTER;
    float dist = length(delta);
    vec2 radialDir = safeNormalize(delta, vec2(0.0, -1.0));
    vec3 baseColor = texture(DiffuseSampler, clampUv(uv)).rgb;
    vec3 finalColor = baseColor;

    if (LayerType == 1) {
        finalColor = applyRadialBlur(uv, radialDir, dist, baseColor);
    } else if (LayerType == 2) {
        finalColor = applySonicPressureBurst(uv, radialDir, dist, baseColor);
    } else if (LayerType == 3) {
        finalColor = applyMotionBlurCore(uv, radialDir, dist, baseColor, false);
    } else if (LayerType == 4) {
        finalColor = applyMotionBlurCore(uv, radialDir, dist, baseColor, true);
    } else if (LayerType == 5) {
        finalColor = applyPressureDistortion(uv, radialDir, dist, baseColor);
    } else if (LayerType == 6) {
        finalColor = applyShockwaveRefraction(uv, radialDir, dist, baseColor);
    } else if (LayerType == 7) {
        finalColor = applyHeatRefraction(uv, radialDir, dist, baseColor);
    } else if (LayerType == 8) {
        finalColor = applyUnderwaterRefraction(uv, radialDir, dist, baseColor);
    } else if (LayerType == 9) {
        finalColor = applyChromaticAberration(uv, radialDir, dist, baseColor);
    } else if (LayerType == 10) {
        finalColor = applyColorGrade(baseColor);
    } else if (LayerType == 11) {
        finalColor = applyDesaturation(baseColor);
    } else if (LayerType == 12) {
        finalColor = applyWhiteout(uv, radialDir, dist, baseColor);
    } else if (LayerType == 13) {
        finalColor = applyDarkVignetteSoft(uv, dist, baseColor);
    } else if (LayerType == 14) {
        finalColor = applyNoiseFog(uv, dist, baseColor);
    } else if (LayerType == 15) {
        finalColor = applyScreenShakeWarp(uv, radialDir, baseColor);
    } else if (LayerType == 16) {
        finalColor = applyGhosting(uv, radialDir, dist, baseColor);
    } else if (LayerType == 17) {
        finalColor = applyTimeWarp(uv, radialDir, dist, baseColor);
    } else if (LayerType == 18) {
        finalColor = applyFractureDistortion(uv, radialDir, dist, baseColor);
    }

    fragColor = vec4(clamp(finalColor, 0.0, 1.0), 1.0);
}
