# 옷장 헤어 윤곽

[옷장 크기에서 이전·현재 헤어 비교](hair-wardrobe.png)

이전에는 준비된 헤어 네 항목이 같은 머리 픽셀 안에서 색과 광택을 바꿨다. 승인 원본의 기본 쇼트 헤어와 포니테일을 그대로 두고 세 가지 헤어를 별도 윤곽으로 만들었다. 기존 선택 번호와 보유 상태를 사용한다.

| 선택 | 도트 디자인 | 편집 원본 |
| --- | --- | --- |
| 4 · 슬림 쇼트 | 낮은 윗머리와 짧게 정리한 귀 옆 | [Aseprite](projects/hair-4.aseprite) |
| 5 · 사이드 파트 | 한쪽으로 넘긴 윗머리와 가르마 | [Aseprite](projects/hair-5.aseprite) |
| 6 · 로우 번 | 목덜미 가까이 묶은 둥근 머리와 작은 끈 | [Aseprite](projects/hair-6.aseprite) |

헤어별로 남녀 각 8포즈에 맞췄다. 기존 머리를 지울 영역과 새로 칠한 머리 레이어를 분리하고 얼굴·귀·몸·옷은 승인 셀에서 가져왔다. 캡은 새 머리 위에 그려지며 위쪽 머리를 눌러 덮고, 낮은 매듭은 뒤에 남는다. 목록에서는 머리 윤곽을 비교할 수 있게 모자를 잠시 숨긴다.

48개 셀을 Aseprite 1.1.6-dev에서 저장하고 다시 내보냈다. 보이는 픽셀이 제작 셀과 일치하고 얼굴·의복·몸 범위를 바꾸지 않는 것을 확인했다. 가장 작은 포즈별 변화는 10,701픽셀이며, 같은 포즈의 두 헤어 사이에서 가장 작은 알파 윤곽 차이는 1,267픽셀이다. 비교표는 호스트에서 64px로 렌더한 결과다.

`author.py`는 승인 파일 해시를 확인하고 포즈별 편집층을 준비한다. 저장 가능한 Aseprite 경로를 넘겨 편집 프로젝트와 런타임 PNG를 내보낸다. 참조 원본과 삭제 범위는 편집 프로젝트의 숨긴 레이어에서 확인할 수 있다.

```sh
python3 docs/design/avatars/hair-shapes-v1/author.py --aseprite /path/to/aseprite --output /tmp/runmoni-hair
```

[승인 원본](../../../../app/src/main/assets/avatars/v0.3.0/running-motion.png) · [Aseprite 파일 형식](https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md)
