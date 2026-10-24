import json

from backend.tests.test_api import pilot  # shared temporary SQLite/TestClient fixture


def own(app, user, items):
    with app.state.db(True) as c:
        c.executemany("INSERT OR IGNORE INTO owned_items VALUES(?,?)", [(user["id"], item) for item in items])


def test_new_catalogue_and_paid_equipment_rejection_are_atomic(pilot):
    _, client, _, account = pilot
    _, h = account("꾸미기 카탈로그")
    items = client.get("/v1/shop/items", headers=h).json()["items"]
    assert len(items) == 62
    assert {"top:4", "bottom:4", "socks:2", "costume:5", "headwear:1", "ear:1"} <= {i["id"] for i in items}
    original = client.get("/v1/me", headers=h).json()
    assert client.patch("/v1/me", headers=h, json={"avatar": {"top": 0, "wrist": 1}}).status_code == 403
    assert client.get("/v1/me", headers=h).json() == original
    assert client.patch("/v1/me", headers=h, json={"avatar": {"costume": 6}}).status_code == 422


def test_all_running_equipment_persists_and_old_client_patch_preserves_it(pilot):
    app, client, _, account = pilot
    user, h = account("同時 장비 러너")
    style = {"hair": 2, "top": 4, "bottom": 4, "socks": 2, "shoes": 1, "headwear": 1, "ear": 1,
             "eyewear": 1, "wrist": 1, "waist": 1, "hydration": 1, "face": 1}
    own(app, user, [f"{k}:{v}" for k, v in style.items()])
    response = client.patch("/v1/me", headers=h, json={"avatar": style})
    assert response.status_code == 200, response.text
    assert all(response.json()["avatar"][k] == v for k, v in style.items())
    old = client.patch("/v1/me", headers=h, json={"avatar": {"hair": 0, "top": 0, "bottom": 0, "shoes": 0, "accessory": 0}})
    assert old.status_code == 200
    for key in ["eyewear", "wrist", "waist", "hydration", "socks", "face"]:
        assert old.json()["avatar"][key] == style[key]


def test_legacy_head_item_alias_does_not_charge_again(pilot):
    app, client, _, account = pilot
    user, h = account("기존 모자 러너")
    own(app, user, ["accessory:2"])
    with app.state.db(True) as c:
        c.execute("UPDATE users SET avatar=? WHERE id=?", (json.dumps({"accessory": 2}), user["id"]))
    profile = client.get("/v1/me", headers=h).json()
    assert profile["avatar"]["headwear"] == 1
    assert "headwear:1" in profile["owned_items"]
    purchase = client.post("/v1/shop/purchase", headers=h, json={"item_id": "headwear:1"})
    assert purchase.status_code == 200 and purchase.json()["already_owned"] is True
    assert purchase.json()["mileage"] == 0
    assert client.patch("/v1/me", headers=h, json={"avatar": {"headwear": 1}}).status_code == 200


def test_costumes_preserve_underlying_clothes_and_exit_on_covered_slot_change(pilot):
    app, client, _, account = pilot
    user, h = account("산타 러너")
    own(app, user, ["costume:1", "costume:3", "top:4", "bottom:4", "wrist:1", "shoes:1"])
    assert client.patch("/v1/me", headers=h, json={"avatar": {"top": 4, "bottom": 4, "costume": 1}}).status_code == 200
    watch = client.patch("/v1/me", headers=h, json={"avatar": {"wrist": 1}}).json()["avatar"]
    assert watch["costume"] == 1 and watch["top"] == 4 and watch["bottom"] == 4
    changed = client.patch("/v1/me", headers=h, json={"avatar": {"top": 0}}).json()["avatar"]
    assert changed["costume"] == 0 and changed["bottom"] == 4
    client.patch("/v1/me", headers=h, json={"avatar": {"costume": 3}})
    shoes = client.patch("/v1/me", headers=h, json={"avatar": {"shoes": 1}}).json()["avatar"]
    assert shoes["costume"] == 3
