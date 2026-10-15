# 브랜드 사용 안내

Kinetica 로고는 둥근 사각 타일 위의 흰 심벌과 워드마크로 이루어집니다. 심벌은 K의 위쪽 팔을 이징 곡선으로 휘게 하고 끝에 키프레임 점을 둔 모양이며, 뒤에 반투명 글래스 판이 비칩니다. FrameKit, PULSE와 같은 배치 규칙을 따르고 색만 다릅니다.

## 파일

| 파일 | 용도 |
|---|---|
| `docs/images/logo-light.png`, `logo-dark.png` | README 머리 로고(884x300, 투명 배경). 밝은 배경과 어두운 배경용 |
| `docs/images/icon.png` | 심벌 타일만 있는 512x512 이미지 |
| `docs/images/brand/mark.svg` | 심벌 원본(220x220) |
| `docs/images/brand/logo-light.svg`, `logo-dark.svg` | 로고 원본. 글자는 윤곽선이라 글꼴 없이 열립니다 |
| `docs/images/brand/render.py` | 위 파일과 샘플 앱 런처 아이콘을 다시 만드는 스크립트 |
| `sample-showcase/src/main/res/drawable/ic_launcher_*.xml` | 샘플 앱 적응형 아이콘. 테마 아이콘(monochrome)은 전경 벡터를 그대로 씁니다 |

심벌이나 색을 바꿀 때는 `render.py`의 값을 고치고 다시 실행합니다. 생성된 SVG, PNG, 벡터 XML은 직접 고치지 않습니다.

```bash
pip install fonttools
python3 docs/images/brand/render.py
```

PNG 변환에는 Chrome headless를 씁니다. 글꼴(Pretendard Bold, Regular, OFL)이 없으면 처음 실행할 때 `~/.cache/kinetica-brand`로 내려받습니다.

## 색

| 이름 | 값 | 쓰는 곳 |
|---|---|---|
| 그라디언트 시작 | `#FF8A5B` | 타일 왼쪽 위 |
| 그라디언트 끝 | `#D6336C` | 타일 오른쪽 아래 |
| 심벌 | `#FFFFFF` | 글래스 판은 불투명도 28% |
| 워드마크(밝은 배경) | `#21141C` | 설명 줄은 `#6E5A66` |
| 워드마크(어두운 배경) | `#FFFFFF` | 설명 줄은 `#E2C3D1` |

배지나 링크 강조처럼 한 가지 색만 쓸 때는 `#D6336C`를 씁니다.

## 크기와 여백

- 타일 모서리 반지름은 한 변의 25%이고, 그라디언트는 왼쪽 위에서 오른쪽 아래로 흐릅니다.
- 심벌은 24px까지 알아볼 수 있습니다. 그보다 작게 쓰지 않습니다.
- 워드마크가 있는 로고는 너비 280px 이상에서 씁니다. 그보다 좁으면 설명 줄이 읽히지 않으므로 심벌만 씁니다.
- 로고 둘레에는 타일 한 변의 25% 이상 빈 공간을 둡니다.
- 심벌을 늘이거나 돌리지 않고, 그라디언트 색을 바꾸거나 그림자, 테두리를 더하지 않습니다.
