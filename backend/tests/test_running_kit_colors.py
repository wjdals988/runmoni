import json

from backend.tests.test_api import pilot  # shared temporary SQLite/TestClient fixture
from backend.tests.test_avatar import own


def test_colors_preserve_existing_mileage_and_do_not_grant_or_require_items(pilot):
    app, client, clock, account = pilot
    user, h = account("러닝복 색상 러너")
    with app.state.db(True) as c:
        c.execute("INSERT INTO ledger VALUES(?,?,NULL,NULL,?,?)", ("kit-existing-credit", user["id"], 777, clock[0]))
    before = client.get("/v1/me", headers=h).json()
    assert before["avatar"]["runningKitColors"] == {"top": 0, "bottom": 0, "shoes": 0}
    response = client.patch("/v1/me", headers=h, json={"avatar": {"runningKitColors": {"top": 5, "bottom": 2, "shoes": 3}}})
    assert response.status_code == 200, response.text
    after = response.json()
    assert after["mileage"] == 777
    assert after["owned_items"] == before["owned_items"]
    assert after["avatar"]["runningKitColors"] == {"top": 5, "bottom": 2, "shoes": 3}
    assert {k: v for k, v in after["avatar"].items() if k != "runningKitColors"} == {
        k: v for k, v in before["avatar"].items() if k != "runningKitColors"
    }
    items = client.get("/v1/shop/items", headers=h).json()["items"]
    assert len(items) == 62 and all(not item["id"].startswith("runningKitColors:") for item in items)
    rejected = client.patch("/v1/me", headers=h, json={"avatar": {"top": 4, "runningKitColors": {"top": 1}}})
    assert rejected.status_code == 403
    assert client.get("/v1/me", headers=h).json() == after


def test_partial_color_and_old_client_patches_preserve_other_regions(pilot):
    _, client, _, account = pilot
    _, h = account("부분 색상 러너")
    selected = client.patch("/v1/me", headers=h, json={"avatar": {
        "gender": "female", "runningKitColors": {"top": 1, "bottom": 2, "shoes": 3}
    }})
    assert selected.status_code == 200
    changed = client.patch("/v1/me", headers=h, json={"avatar": {"runningKitColors": {"top": 4}}})
    assert changed.status_code == 200
    expected = {"top": 4, "bottom": 2, "shoes": 3}
    assert changed.json()["avatar"]["runningKitColors"] == expected
    for patch in [
        {"hair": 0, "top": 0, "bottom": 0, "shoes": 0, "accessory": 0},
        {"gender": "male"},
        {"runningKitColors": {}},
    ]:
        response = client.patch("/v1/me", headers=h, json={"avatar": patch})
        assert response.status_code == 200, response.text
        assert response.json()["avatar"]["runningKitColors"] == expected
    assert client.get("/v1/me", headers=h).json()["avatar"]["runningKitColors"] == expected


def test_colors_keep_costume_and_underlying_item_selections(pilot):
    app, client, _, account = pilot
    user, h = account("세트 색상 러너")
    own(app, user, ["top:4", "bottom:4", "costume:1"])
    first = client.patch("/v1/me", headers=h, json={"avatar": {"top": 4, "bottom": 4, "costume": 1}})
    assert first.status_code == 200
    colored = client.patch("/v1/me", headers=h, json={"avatar": {"runningKitColors": {"top": 2}}})
    assert colored.status_code == 200
    avatar = colored.json()["avatar"]
    assert avatar["top"] == 4 and avatar["bottom"] == 4 and avatar["costume"] == 1
    assert avatar["runningKitColors"] == {"top": 2, "bottom": 0, "shoes": 0}
    old_slot_change = client.patch("/v1/me", headers=h, json={"avatar": {"bottom": 0}})
    assert old_slot_change.status_code == 200
    assert old_slot_change.json()["avatar"]["costume"] == 0
    assert old_slot_change.json()["avatar"]["runningKitColors"] == avatar["runningKitColors"]


def test_invalid_color_requests_are_strict_and_atomic(pilot):
    _, client, _, account = pilot
    _, h = account("색상 검증 러너")
    before = client.get("/v1/me", headers=h).json()
    for colors in [
        None, "unexpected", [], {"top": -1}, {"top": 6}, {"top": 1.5},
        {"top": "3"}, {"top": True}, {"bottom": None}, {"shoes": 9999999999}, {"face": 1},
    ]:
        response = client.patch("/v1/me", headers=h, json={"avatar": {"gender": "female", "runningKitColors": colors}})
        assert response.status_code == 422, (colors, response.text)
        assert client.get("/v1/me", headers=h).json() == before


def test_malformed_stored_colors_normalize_without_losing_other_profile_data(pilot):
    app, client, _, account = pilot
    user, h = account("기존 색상 복구")
    for colors, expected in [
        (None, {"top": 0, "bottom": 0, "shoes": 0}),
        ("unexpected", {"top": 0, "bottom": 0, "shoes": 0}),
        ({"top": "3", "bottom": 4, "shoes": False}, {"top": 0, "bottom": 4, "shoes": 0}),
        ({"top": -1, "bottom": 2, "shoes": 9999999999}, {"top": 0, "bottom": 2, "shoes": 0}),
    ]:
        saved = {"gender": "female", "top": 2, "wrist": 1, "runningKitColors": colors}
        with app.state.db(True) as c:
            c.execute("UPDATE users SET avatar=? WHERE id=?", (json.dumps(saved), user["id"]))
        profile = client.get("/v1/me", headers=h).json()
        assert profile["nickname"] == "기존 색상 복구"
        assert profile["avatar"]["gender"] == "female"
        assert profile["avatar"]["top"] == 2 and profile["avatar"]["wrist"] == 1
        assert profile["avatar"]["runningKitColors"] == expected
