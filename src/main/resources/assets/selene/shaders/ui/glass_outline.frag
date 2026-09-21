#version 330 core

in vec2 vLocalPx;
in vec2 vPosPx;
flat in vec2 vSize;
flat in vec4 vRadii;
flat in vec4 vAlphaPowerMix;
flat in vec4 vFresnel;
flat in vec4 vFlags;
flat in vec4 vTint;

uniform sampler2D uBlur;
uniform vec2 uBlurScale;
uniform vec2 uBlurOffset;
uniform vec4 uScissor;
uniform float uScissorEnabled;

out vec4 FragColor;

float rdist(vec2 pos, vec2 size, vec4 radius) {
    float cornerRadius;
    if (pos.x > 0.0) {
        cornerRadius = (pos.y > 0.0) ? radius.x : radius.w;
    } else {
        cornerRadius = (pos.y > 0.0) ? radius.y : radius.z;
    }

    vec2 v = abs(pos) - size + cornerRadius;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - cornerRadius;
}

const float EDGE_SOFTNESS = 2.0;

float coverage(float d, float px) {
    float t = clamp(0.5 - 0.5 * d / (EDGE_SOFTNESS * px), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float pixelSize(vec2 localPx) {
    return max(0.5 * (length(dFdx(localPx)) + length(dFdy(localPx))), 1e-4);
}

vec2 sdfNormal(vec2 p, vec2 halfSize, vec4 radius) {
    float eps = 1.0;
    vec2 g = vec2(
        rdist(p + vec2(eps, 0.0), halfSize, radius) - rdist(p - vec2(eps, 0.0), halfSize, radius),
        rdist(p + vec2(0.0, eps), halfSize, radius) - rdist(p - vec2(0.0, eps), halfSize, radius)
    );
    return (length(g) > 0.0001) ? normalize(g) : vec2(0.0, 1.0);
}

void main() {
    float px = pixelSize(vLocalPx);
    if (uScissorEnabled > 0.5) {
        if (vPosPx.x < uScissor.x || vPosPx.y < uScissor.y
            || vPosPx.x > uScissor.z || vPosPx.y > uScissor.w) {
            discard;
        }
    }

    vec2 size = max(vSize, vec2(1.0));
    float thickness = max(vFlags.z, 0.0);
    float globalAlpha = clamp(vAlphaPowerMix.x, 0.0, 1.0);
    float baseAlpha = clamp(vAlphaPowerMix.z, 0.0, 1.0);
    float fresnelMix = clamp(vAlphaPowerMix.w, 0.0, 1.0);

    vec2 halfSize = size * 0.5;
    vec2 pos = halfSize - vLocalPx;
    vec4 radii = max(vRadii, vec4(0.0));
    float alpha = coverage(rdist(pos, halfSize, radii), px);
    vec2 halfInner = halfSize - vec2(thickness);
    if (halfInner.x > 0.0 && halfInner.y > 0.0) {
        alpha -= coverage(rdist(pos, halfInner, max(radii - vec4(thickness), vec4(0.0))), px);
    }
    alpha = clamp(alpha, 0.0, 1.0);
    vec2 normal = sdfNormal(pos, halfSize, radii);

    
    vec2 uv = clamp(vPosPx * uBlurScale + uBlurOffset, 0.0, 1.0);
    vec3 bg = texture(uBlur, uv).rgb;

    
    vec2 lightDir = normalize(vec2(-0.45, -0.9)); 
    float facing = clamp(dot(normal, -lightDir), 0.0, 1.0);
    float rim = pow(facing, 2.4) * clamp(fresnelMix * 0.7, 0.0, 0.35);

    vec3 color = mix(bg, vec3(vFresnel.rgb), rim);
    color = mix(color, vec3(vTint.rgb), rim * clamp(vTint.a, 0.0, 1.0) * 0.5);

    float finalAlpha = (baseAlpha + rim) * alpha * globalAlpha;
    if (finalAlpha < 0.001) {
        discard;
    }

    FragColor = vec4(color * finalAlpha, finalAlpha);
}