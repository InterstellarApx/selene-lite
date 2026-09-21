#version 330 core
layout(location = 0) in vec2 aLocal;
layout(location = 1) in vec4 aRect;
layout(location = 2) in vec4 aRadii;
layout(location = 3) in vec4 aAlphaPowerMix;
layout(location = 4) in vec4 aFresnel;
layout(location = 5) in vec4 aFlags;
layout(location = 6) in vec4 aTint;

uniform vec2 uViewport;
uniform float uGuiScale;

out vec2 vLocalPx;
out vec2 vPosPx;
flat out vec2 vSize;
flat out vec4 vRadii;
flat out vec4 vAlphaPowerMix;
flat out vec4 vFresnel;
flat out vec4 vFlags;
flat out vec4 vTint;

const float BEZEL = 14.0;
const float SHADOW_REACH = 0.8;
const float SHADOW_DROP = 1.5;

void main() {
    vec2 size = aRect.zw;
    float scale = max(uGuiScale, 1.0);
    float lift = min(min(size.x, size.y) * 0.5, BEZEL * scale);
    float pad = lift * SHADOW_REACH + (2.0 * SHADOW_DROP + 1.0) * scale + 2.0;

    vSize = size;
    vec2 local = aLocal + (aLocal * 2.0 - 1.0) * pad / max(abs(size), vec2(1.0));
    vLocalPx = local * size;
    vPosPx = aRect.xy + vLocalPx;
    vRadii = aRadii;
    vAlphaPowerMix = aAlphaPowerMix;
    vFresnel = aFresnel;
    vFlags = aFlags;
    vTint = aTint;

    vec2 ndc = vec2(
        (vPosPx.x / uViewport.x) * 2.0 - 1.0,
        1.0 - (vPosPx.y / uViewport.y) * 2.0
    );
    gl_Position = vec4(ndc, 0.0, 1.0);
}
