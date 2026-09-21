#version 330 core

in vec2 vLocalPx;
in vec2 vPosPx;
flat in vec2 vSize;
flat in vec4 vRadii;
flat in vec4 vAlphaPowerMix;
flat in vec4 vFlags;
flat in vec4 vTint;

uniform sampler2D uBlur;
uniform vec2 uBlurScale;
uniform vec2 uBlurOffset;
uniform vec4 uScissor;
uniform float uScissorEnabled;
uniform float uGuiScale;

out vec4 FragColor;

const float EDGE_SOFTNESS = 2.0;
const float REFRACTIVE_INDEX = 1.5;
const float DISPERSION = 0.18;
const float BEZEL = 14.0;
const float BEZEL_FRACTION = 0.25;
const float THICKNESS = 1.8;
const float SATURATION = 1.2;
const float TINT = 0.22;
const float REFLECTANCE = 0.04;
const float RIM = 0.9;
const float RIM_AMBIENT = 0.3;
const float HIGHLIGHT = 1.0;
const float COUNTER_HIGHLIGHT = 0.4;
const float SUPERSAMPLE_BAND = 0.35;
const float SHADOW_DROP = 1.5;
const float SHADOW_SPREAD = 0.3;
const float SHADOW_STRENGTH = 0.24;
const vec2 LIGHT = vec2(-0.33, -0.94);
const vec3 DARK_TINT = vec3(20.0 / 255.0);
const vec3 FROST_TINT = vec3(0.80, 0.84, 0.90);
const float FROST_TINT_AMOUNT = 0.34;
const float FROST_SATURATION = 0.8;
const float FROST_GRAIN = 0.018;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float cornerRadius(vec2 p, vec4 radii) {
    if (p.x < 0.0) {
        return p.y < 0.0 ? radii.x : radii.w;
    }
    return p.y < 0.0 ? radii.y : radii.z;
}

float boxDistance(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

vec2 boxGradient(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    vec2 direction = q.x > 0.0 && q.y > 0.0 ? normalize(q) : (q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
    return direction * sign(p);
}

float coverage(float d, float px) {
    float t = clamp(0.5 - 0.5 * d / (EDGE_SOFTNESS * px), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float pixelSize(vec2 localPx) {
    return max(0.5 * (length(dFdx(localPx)) + length(dFdy(localPx))), 1e-4);
}

vec3 backdrop(vec2 offsetPx) {
    vec2 uv = clamp((vPosPx + offsetPx) * uBlurScale + uBlurOffset, 0.0, 1.0);
    return texture(uBlur, uv).rgb;
}

float grain(vec2 pixel) {
    return fract(sin(dot(floor(pixel), vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

float surface(float x) {
    return sqrt(max(1.0 - (1.0 - x) * (1.0 - x), 0.0));
}

vec3 surfaceNormal(float edge, float bezel, vec2 outward) {
    if (edge >= bezel) {
        return vec3(0.0, 0.0, 1.0);
    }
    float x = clamp(edge / bezel, 0.002, 1.0);
    float slope = (1.0 - x) / max(surface(x), 0.001);
    return normalize(vec3(outward * slope * THICKNESS, 1.0));
}

vec3 refracted(vec2 offsetPx, float edge, float bezel, vec2 outward) {
    if (edge >= bezel) {
        return backdrop(offsetPx);
    }
    vec3 ray = refract(vec3(0.0, 0.0, -1.0), surfaceNormal(edge, bezel, outward), 1.0 / REFRACTIVE_INDEX);
    vec2 shift = ray.xy / max(-ray.z, 0.001) * bezel * THICKNESS * surface(clamp(edge / bezel, 0.002, 1.0));
    return vec3(
        backdrop(offsetPx + shift * (1.0 - DISPERSION)).r,
        backdrop(offsetPx + shift).g,
        backdrop(offsetPx + shift * (1.0 + DISPERSION)).b);
}

void main() {
    float px = pixelSize(vLocalPx);

    if (uScissorEnabled > 0.5) {
        if (vPosPx.x < uScissor.x || vPosPx.y < uScissor.y
            || vPosPx.x > uScissor.z || vPosPx.y > uScissor.w) {
            discard;
        }
    }

    float scale = max(uGuiScale, 1.0);
    vec2 halfSize = max(vSize, vec2(1.0)) * 0.5;
    vec2 p = vLocalPx - halfSize;
    float shortSide = min(halfSize.x, halfSize.y) * 2.0;
    float corner = min(cornerRadius(p, max(vRadii, vec4(0.0))), shortSide * 0.5);
    float bezel = min(shortSide * BEZEL_FRACTION, BEZEL * scale);
    float lift = min(shortSide * 0.5, BEZEL * scale);

    float dist = boxDistance(p, halfSize, corner);
    float cover = coverage(dist, px);
    float alpha = 0.0;
    vec3 color = vec3(0.0);

    if (cover < 1.0 && vFlags.x > 0.5) {
        float shadowDistance = boxDistance(p - vec2(0.0, SHADOW_DROP * scale), halfSize, corner);
        float spread = SHADOW_SPREAD * lift;
        float shadow = shadowDistance > 0.0
            ? SHADOW_STRENGTH * exp(-(shadowDistance * shadowDistance) / (2.0 * spread * spread))
            : SHADOW_STRENGTH;
        alpha += shadow * (1.0 - cover);
    }

    if (cover > 0.0) {
        vec2 outward = boxGradient(p, halfSize, corner);
        float edge = max(-dist, 0.0);
        vec3 glass;
        if (edge < bezel * SUPERSAMPLE_BAND) {
            float tap = 0.25 * px;
            glass = 0.5 * (refracted(-outward * tap, edge + tap, bezel, outward)
                + refracted(outward * tap, max(edge - tap, 0.0), bezel, outward));
        } else {
            glass = refracted(vec2(0.0), edge, bezel, outward);
        }

        float grey = dot(glass, LUMA);
        float frost = clamp(vAlphaPowerMix.w, 0.0, 1.0);
        glass = clamp(grey + (glass - grey) * mix(SATURATION, FROST_SATURATION, frost), 0.0, 1.0);
        glass = mix(glass, mix(DARK_TINT, FROST_TINT, frost), mix(TINT, FROST_TINT_AMOUNT, frost));
        glass += grain(vPosPx) * FROST_GRAIN * frost;
        glass = mix(glass, vTint.rgb, clamp(vTint.a, 0.0, 1.0));

        vec3 normal = surfaceNormal(edge, bezel, outward);
        float fresnel = (1.0 - REFLECTANCE) * pow(1.0 - normal.z, 5.0);
        float facing = dot(outward, normalize(LIGHT));
        float lit = RIM_AMBIENT + HIGHLIGHT * max(facing, 0.0) + COUNTER_HIGHLIGHT * max(-facing, 0.0);
        glass = mix(glass, vec3(1.0), clamp(fresnel * RIM * lit, 0.0, 1.0));

        color = glass * cover;
        alpha += cover;
    }

    float globalAlpha = clamp(vAlphaPowerMix.x, 0.0, 1.0);
    if (alpha * globalAlpha < 0.001) {
        discard;
    }
    FragColor = vec4(color, alpha) * globalAlpha;
}
