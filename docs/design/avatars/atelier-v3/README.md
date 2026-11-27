# 러닝 미니미 의상과 장비

[남자 8포즈](production-fit-male.png) · [여자 8포즈](production-fit-female.png) · [밝은 화면과 어두운 화면의 옷장 크기 비교](production-wardrobe.png)

| 방향 | 모자 | 상의와 하의 | 신발 | 장비 |
| --- | --- | --- | --- | --- |
| 레이스 | [선바이저](projects/headwear-7.aseprite) | [싱글릿](projects/top-10.aseprite), [스플릿 하의](projects/bottom-9.aseprite) | [폼 러닝화](projects/shoes-8.aseprite) | [젤 벨트](projects/waist-7.aseprite) |
| 야간 도심 | [패널 캡](projects/headwear-8.aseprite) | [방풍 셸](projects/top-11.aseprite), [컴프레션 타이츠](projects/bottom-10.aseprite) | [리플렉트 러닝화](projects/shoes-9.aseprite) | [로우프로파일 실드](projects/eyewear-7.aseprite) |
| 트레일 | [헤드밴드](projects/headwear-9.aseprite) | [기능성 티](projects/top-12.aseprite), [카고 하의](projects/bottom-11.aseprite) | [러그 러닝화](projects/shoes-10.aseprite) | [듀얼 플라스크 조끼](projects/hydration-7.aseprite) |

레이스의 챙 밑면과 얇은 싱글릿, 야간 도심의 캡 패널과 지퍼 옆 반사 띠, 트레일의 플라스크와 카고 포켓을 부위별로 맞췄다. 용도가 다른 뒤꿈치 보강과 밑창 패턴을 16포즈에 각각 배치했다. 여자 레이스 하의는 승인 원본의 긴 다리 의복 위에 짧은 패널을 올려 같은 각도의 다리 형태를 유지한다. 조끼와 벨트, 실드는 상·하의와 별도 파일이다.

정지 시안 [2차 시안](../../../app-review/2026-11-27-outfit-fitting-v2/README.md)의 일부를 실제 착용 레이어로 분리했다. 승인된 남녀 러닝 원본의 프레임 번호와 256×384 셀을 썼다. 손 보호판 위로 장비를 그리지 않았다. 남자 세 번째 포즈에서 손 보호판에 포함됐던 붉은 의복과 봉제선만 재분류했다. 실드는 지정한 눈 근처와 프레임의 접점에 들어간다. 모자와 조끼 옆 메시 포켓은 윤곽 밖에 그릴 수 있어 두 파일 유형의 제한 영역에서만 확장한다.

포즈별 좌표에서 픽셀을 맞추고 Aseprite 1.1.6 배치판으로 편집 원본과 PNG를 저장했다. 각 프로젝트에는 남녀별 8셀과 편집 가능한 부위 레이어가 들어 있다. 투명 레이어를 배포 셀로 내보낸 뒤 저장 전 합성과 보이는 픽셀을 비교했다. 상·하의와 신발의 원단은 기존 색상 역할을 사용하고 배번·반사·봉제에는 고정 색을 썼다. 미리보기는 생산 PNG를 합성한 호스트 출력이다.

재생성과 검사에는 Python, Pillow 12 이상, 저장을 지원하는 Aseprite를 사용한다. 두 명령은 별도로 실행하며 `--output`에는 비어 있는 작업 디렉터리를 지정한다.

```sh
python3 docs/design/avatars/atelier-v3/export.py --aseprite /path/to/aseprite --output /tmp/runmoni-atelier-v3
python3 docs/design/avatars/atelier-v3/export.py --verify app/src/main/assets/avatars/atelier-v3 --projects docs/design/avatars/atelier-v3/projects
```

[승인 러닝 원본](../../../../app/src/main/assets/avatars/reference-review/approved-running.png) · [Aseprite 파일 형식](https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md)
