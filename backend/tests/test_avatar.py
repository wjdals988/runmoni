import json

from backend.tests.test_api import pilot  # shared temporary SQLite/TestClient fixture


def own(app, user, items):
    with app.state.db(True) as c:
        c.executemany("INSERT OR IGNORE INTO owned_items VALUES(?,?)", [(user["id"], item) for item in items])


def test_catalogue_and_unclaimed_equipment_rejection_are_atomic(pilot):
    _, client, _, account = pilot
    _, h = account("꾸미기 카탈로그")
    items = client.get("/v1/shop/items", headers=h).json()["items"]
    assert len(items) == 65
    assert {"top:4", "bottom:4", "socks:2", "costume:5", "headwear:1", "ear:1", "top:6", "socks:4", "shoes:4"} <= {i["id"] for i in items}
    # Original catalogue response prefix remains byte-for-byte in identity/order.
    old_slots = ["hair", "top", "bottom", "shoes", "accessory", "eyewear", "wrist", "waist", "hydration",
                 "face", "socks", "headwear", "ear", "costume"]
    original_ids = [f"{slot}:{value}" for slot in old_slots
                    for value in range(6 if slot in {"top", "bottom", "costume"} else 4)]
    assert [item["id"] for item in items[:62]] == original_ids
    assert [item["id"] for item in items[62:]] == ["top:6", "socks:4", "shoes:4"]
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


def test_explicit_gender_round_trip_and_old_client_patch_preserve_the_base(pilot):
    app, client, _, account = pilot
    user, h = account("기본형 선택 러너")
    before = client.get("/v1/me", headers=h).json()
    assert before["avatar"]["gender"] == "male"
    female = client.patch("/v1/me", headers=h, json={"avatar": {"gender": "female"}})
    assert female.status_code == 200, female.text
    assert female.json()["avatar"]["gender"] == "female"
    assert female.json()["owned_items"] == before["owned_items"]
    assert female.json()["mileage"] == before["mileage"]
    old = client.patch("/v1/me", headers=h, json={"avatar": {"hair": 0, "top": 0}})
    assert old.status_code == 200
    assert old.json()["avatar"]["gender"] == "female"
    assert client.get("/v1/me", headers=h).json()["avatar"]["gender"] == "female"
    assert client.patch("/v1/me", headers=h, json={"avatar": {"gender": "unknown"}}).status_code == 422
    male = client.patch("/v1/me", headers=h, json={"avatar": {"gender": "male"}})
    assert male.status_code == 200 and male.json()["avatar"]["gender"] == "male"


def test_all_test_items_are_free_and_claims_preserve_existing_mileage(pilot):
    app, client, clock, account = pilot
    user, h = account("무료 옷장 러너")
    with app.state.db(True) as c:
        c.execute("INSERT INTO ledger VALUES(?,?,NULL,NULL,?,?)", ("existing-test-credit", user["id"], 777, clock[0]))
    before = client.get("/v1/me", headers=h).json()
    assert before["mileage"] == 777
    items = client.get("/v1/shop/items", headers=h).json()["items"]
    assert len(items) == 65 and all(item["cost"] == 0 for item in items)
    for item in items:
        first = client.post("/v1/shop/purchase", headers=h, json={"item_id": item["id"]})
        assert first.status_code == 200, first.text
        assert first.json()["mileage"] == 777
        repeat = client.post("/v1/shop/purchase", headers=h, json={"item_id": item["id"]})
        assert repeat.status_code == 200 and repeat.json()["already_owned"] is True
        assert repeat.json()["mileage"] == 777
    after = client.get("/v1/me", headers=h).json()
    assert set(before["owned_items"]) <= set(after["owned_items"])
    assert {item["id"] for item in items} <= set(after["owned_items"])


def test_appended_materials_claim_and_patch_without_resetting_old_ids_gender_or_balance(pilot):
    app, client, clock, account = pilot
    user, h = account("추가 소재 러너")
    with app.state.db(True) as c:
        c.execute("INSERT INTO ledger VALUES(?,?,NULL,NULL,?,?)", ("atelier-credit", user["id"], 777, clock[0]))
    own(app, user, ["top:4", "socks:3", "costume:1", "wrist:2"])
    seeded = client.patch("/v1/me", headers=h, json={"avatar": {
        "top": 4, "socks": 3, "costume": 1, "wrist": 2, "gender": "female",
        "runningKitColors": {"top": 4, "bottom": 2, "shoes": 3}}})
    assert seeded.status_code == 200, seeded.text
    before = seeded.json()
    assert client.patch("/v1/me", headers=h, json={"avatar": {"top": 6}}).status_code == 403
    assert client.get("/v1/me", headers=h).json() == before
    for item in ["top:6", "socks:4", "shoes:4"]:
        claimed = client.post("/v1/shop/purchase", headers=h, json={"item_id": item})
        assert claimed.status_code == 200 and claimed.json()["mileage"] == 777
        repeated = client.post("/v1/shop/purchase", headers=h, json={"item_id": item})
        assert repeated.status_code == 200 and repeated.json()["already_owned"] is True
        assert repeated.json()["mileage"] == 777
    equipped = client.patch("/v1/me", headers=h, json={"avatar": {"top": 6, "socks": 4, "shoes": 4}})
    assert equipped.status_code == 200, equipped.text
    after = equipped.json()
    assert after["avatar"]["top"] == 6 and after["avatar"]["socks"] == 4 and after["avatar"]["shoes"] == 4
    assert after["avatar"]["costume"] == 0 and after["avatar"]["wrist"] == 2
    assert after["avatar"]["gender"] == "female"
    assert after["avatar"]["runningKitColors"] == before["avatar"]["runningKitColors"]
    assert set(before["owned_items"]) <= set(after["owned_items"])
    assert {"top:6", "socks:4", "shoes:4"} <= set(after["owned_items"])
    assert after["mileage"] == 777
    old_client = client.patch("/v1/me", headers=h, json={"avatar": {"hair": 0}})
    assert old_client.status_code == 200 and old_client.json() == after
    for slot, invalid in [("top", 7), ("socks", 5), ("shoes", 5)]:
        assert client.patch("/v1/me", headers=h, json={"avatar": {slot: invalid}}).status_code == 422
        assert client.get("/v1/me", headers=h).json() == after
