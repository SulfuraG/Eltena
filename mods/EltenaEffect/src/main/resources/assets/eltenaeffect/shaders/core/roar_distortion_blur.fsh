#version 150

uniform sampler2D DiffuseSampler;
uniform int LayerType;
uniform float Strength;
uniform float BlurRadius;
uniform float Progress;
uniform float FocusLoss;
uniform float PressurePeak;
uniform float Aftershock;
uniform int SampleCount;
uniform float Frequency;
uniform float Speed;
uniform int EdgeOnly;
uniform int DirectionMode;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

const int MAX_SAMPLES = 16;
const vec2 SCREEN_CENTER = vec2(0.5, 0.5);

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec2 perpendicular(vec2 value) {
    return vec2(-value.y, value.x);
}

vec3 sampleDirectionalBlur(
    vec2 uv,
    vec2 primaryDir,
    vec2 secondaryDir,
    float radius,
    int sampleCount,
    float secondaryScale
) {
    vec3 accum = texture(DiffuseSampler, uv).rgb * 1.25;
    float totalWeight = 1.25;

    for (int i = 0; i < MAX_SAMPLES; i++) {
        if (i >= sampleCount) {
            break;
        }

        float t = float(i + 1) / float(sampleCount);
        float weight = 1.0 - (t * 0.78);
        vec2 primaryOffset = primaryDir * radius * t;
        vec2 secondaryOffset = secondaryDir * radius * t * secondaryScale;

        accum += texture(DiffuseSampler, uv - primaryOffset - (secondaryOffset * 0.35)).rgb * weight;
        accum += texture(DiffuseSampler, uv + (primaryOffset * 0.55) + secondaryOffset).rgb * (weight * 0.58);
        totalWeight += weight + (weight * 0.58);
    }

    return accum / max(totalWeight, 0.0001);
}

vec3 applyRadialBlur(vec2 uv, vec2 dir, vec2 tangent, float dist, vec3 baseColor, float focus, float pressure, float residual) {
    float blurEnvelope = clamp((focus * 0.78) + (pressure * 0.58) + (residual * 0.24), 0.0, 2.0);
    float radius = BlurRadius * (0.0011 + (dist * 0.0095)) * (0.22 + (Strength * (0.65 + (blurEnvelope * 0.35))));
    int sampleCount = clamp(SampleCount, 1, MAX_SAMPLES);
    vec3 blurred = sampleDirectionalBlur(
        uv,
        dir,
        tangent,
        radius,
        sampleCount,
        0.16 + (pressure * 0.08)
    );
    float edgeMask = smoothstep(0.03, 0.32, dist);
    float centerRecover = 1.0 - smoothstep(0.0, 0.22, dist);
    float mixFactor = saturate(
        (Strength * (0.24 + (blurEnvelope * 0.26)))
            + (edgeMask * pressure * 0.16)
            + (centerRecover * focus * 0.12)
    );
    return mix(baseColor, blurred, mixFactor);
}

vec3 applyPressureDistortion(vec2 uv, vec2 dir, vec2 tangent, float dist, vec3 baseColor, float focus, float pressure, float residual) {
    float frequency = max(Frequency, 0.01);
    float speed = max(Speed, 0.01);
    float midMask = smoothstep(0.03, 0.14, dist) * (1.0 - smoothstep(0.80, 1.08, dist));
    float front = exp(-pow((dist - (0.18 + (Progress * 0.58))) * (4.2 + (frequency * 0.22)), 2.0));
    float wave = sin((dist * (14.0 + (frequency * 3.2))) - (Time * (2.0 + (speed * 6.0))) + (Progress * 8.0));
    float shearing = cos((dot(uv, vec2(41.0, 27.0))) + (Time * (1.4 + (speed * 5.0))));
    float amplitude = Strength
        * (0.0026 + (pressure * 0.0028) + (residual * 0.0015) + (focus * 0.0008))
        * ((wave * midMask) + (front * 0.85));
    vec2 distortedUv = uv + (dir * amplitude) + (tangent * amplitude * 0.55 * shearing);
    distortedUv = clamp(distortedUv, vec2(0.001), vec2(0.999));
    vec3 distortedColor = texture(DiffuseSampler, distortedUv).rgb;
    float mixFactor = saturate(Strength * (0.82 + (pressure * 0.22) + (residual * 0.12)));
    return mix(baseColor, distortedColor, mixFactor);
}

vec3 applyChromaticAberration(vec2 uv, vec2 dir, float dist, vec3 baseColor, float pressure, float residual) {
    float edgeMask = EdgeOnly == 1 ? smoothstep(0.08, 0.42, dist) : 1.0;
    float offset = Strength
        * edgeMask
        * (0.55 + (pressure * 0.35) + (residual * 0.12))
        * (0.45 + (dist * 1.1));
    vec2 shift = dir * offset;
    vec3 shifted = vec3(
        texture(DiffuseSampler, clamp(uv + shift, vec2(0.001), vec2(0.999))).r,
        baseColor.g,
        texture(DiffuseSampler, clamp(uv - (shift * 0.85), vec2(0.001), vec2(0.999))).b
    );
    float mixFactor = saturate(Strength * 42.0) * edgeMask;
    return mix(baseColor, shifted, mixFactor);
}

vec3 applyMotionBlur(vec2 uv, vec2 dir, float dist, vec3 baseColor, float focus, float pressure) {
    float directionSign = DirectionMode == 1 ? -1.0 : 1.0;
    float radius = (0.0010 + (dist * 0.0064)) * (0.45 + (Strength * 1.20) + (pressure * 0.20));
    int sampleCount = clamp(4 + int(Strength * 8.0), 4, 10);
    vec3 blurred = sampleDirectionalBlur(
        uv,
        dir * directionSign,
        vec2(0.0, 0.0),
        radius,
        sampleCount,
        0.0
    );
    float mixFactor = saturate(Strength * (0.72 + (focus * 0.08) + (pressure * 0.18)));
    return mix(baseColor, blurred, mixFactor);
}

void main() {
    vec2 uv = texCoord;
    vec2 delta = uv - SCREEN_CENTER;
    float dist = length(delta);
    vec2 dir = dist > 0.0001 ? delta / dist : vec2(0.0, -1.0);
    vec2 tangent = perpendicular(dir);
    float focus = clamp(FocusLoss, 0.0, 2.0);
    float pressure = clamp(PressurePeak, 0.0, 2.0);
    float residual = clamp(Aftershock, 0.0, 2.0);
    vec3 baseColor = texture(DiffuseSampler, uv).rgb;
    vec3 finalColor = baseColor;

    if (LayerType == 1) {
        finalColor = applyRadialBlur(uv, dir, tangent, dist, baseColor, focus, pressure, residual);
    } else if (LayerType == 2) {
        finalColor = applyPressureDistortion(uv, dir, tangent, dist, baseColor, focus, pressure, residual);
    } else if (LayerType == 3) {
        finalColor = applyChromaticAberration(uv, dir, dist, baseColor, pressure, residual);
    } else if (LayerType == 4) {
        finalColor = applyMotionBlur(uv, dir, dist, baseColor, focus, pressure);
    }

    fragColor = vec4(finalColor, 1.0);
}
