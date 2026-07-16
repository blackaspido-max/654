# Third-party notices

## llama.cpp

NightMaster v0.5 uses the Android example and native inference engine from
[`ggml-org/llama.cpp`](https://github.com/ggml-org/llama.cpp).

Pinned source revision:

`8ee54c8b32a1b0cf13c03fc5723142bc62c775f6`

License: MIT.

The required license text is preserved at:

`third_party/llama.cpp/LICENSE`

NightMaster applies the reviewable patch
`patches/llama-android-nightmaster-v05.patch` to that exact revision during the
GitHub Actions build. The upstream source itself is not silently replaced or
fetched from a moving branch.

Additional Android and Kotlin dependencies retain their own upstream license
notices. A complete in-app licenses screen will be prepared before the first
public release build.
