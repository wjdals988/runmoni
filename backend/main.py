"""Local adult-attested pilot. SQLite transactions are the consistency boundary."""
import hashlib
import hmac
import json
import logging
import math
import os
import secrets
import sqlite3
import threading
import time
from collections import defaultdict, deque
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Literal
from uuid import UUID, uuid4

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .models import (CrewCreate, CrewJoin, ItemPurchase, MessageCreate, MusicLease,
                     PartyCreate, PilotLogin, PresenceUpdate, ProfilePatch,
                     RolePatch, RoomRequest, RunCreate, RunFinalize, TrackBatch,
                     UserBlock)

VERSION = "0.1.4"
SEOUL = timezone(timedelta(hours=9))
AVATAR_SLOTS = {"hair": "헤어", "top": "상의", "bottom": "하의", "shoes": "신발", "accessory": "머리 장비",
                "eyewear": "선글라스", "wrist": "워치", "waist": "러닝 벨트", "hydration": "수분 조끼",
                "face": "얼굴", "socks": "양말", "headwear": "모자", "ear": "귀 장비", "costume": "특수 코스튬"}
CATALOGUE = [{"id": f"{slot}:{variant}", "name": f"{label} {variant + 1}", "cost": [0, 500, 1000, 2000, 500, 1000][variant], "slot": slot, "value": variant}
             for slot, label in AVATAR_SLOTS.items() for variant in range(6 if slot in {"top", "bottom", "costume"} else 4)]


def canonical_avatar(style):
    result = {slot: style.get(slot, 0) or 0 for slot in AVATAR_SLOTS}
    legacy = style.get("accessory", 0)
    if style.get("headwear") is None:
        result["headwear"] = {1: 3, 2: 1}.get(legacy, 0)
    if style.get("ear") is None:
        result["ear"] = 1 if legacy == 3 else 0
    return result


def migrated_owned(items):
    result = set(items) | {f"{slot}:0" for slot in AVATAR_SLOTS}
    for old, new in [("accessory:1", "headwear:3"), ("accessory:2", "headwear:1"), ("accessory:3", "ear:1")]:
        if old in result:
            result.add(new)
    return sorted(result)


def costume_covers(costume):
    if costume in {1, 4, 5}:
        return {"headwear", "top", "bottom", "socks", "shoes"}
    return {"top", "bottom"} if costume in {2, 3} else set()
LOG = logging.getLogger("runmoni")


@dataclass
class Settings:
    db_path: str = str(Path(__file__).with_name("data") / "pilot.sqlite3")
    pilot_invite: str = ""
    livekit_url: str = ""
    livekit_api_key: str = ""
    livekit_api_secret: str = ""
    bridge_url: str = ""
    bridge_secret: str = ""
    bridge_control_secret: str = ""
    reward_daily_limit: int = 10000
    cors_origins: tuple[str, ...] = ("http://127.0.0.1:8787", "http://localhost:8787")

    @classmethod
    def from_env(cls):
        return cls(
            db_path=os.getenv("RUNMONI_DB_PATH", cls.db_path),
            pilot_invite=os.getenv("RUNMONI_PILOT_INVITE", ""),
            livekit_url=os.getenv("LIVEKIT_URL", ""),
            livekit_api_key=os.getenv("LIVEKIT_API_KEY", ""),
            livekit_api_secret=os.getenv("LIVEKIT_API_SECRET", ""),
            bridge_url=os.getenv("AUDIO_BRIDGE_URL", ""),
            bridge_secret=os.getenv("AUDIO_BRIDGE_JWT_SECRET", ""),
            bridge_control_secret=os.getenv("AUDIO_BRIDGE_CONTROL_SECRET", ""),
            reward_daily_limit=int(os.getenv("RUNMONI_REWARD_DAILY_LIMIT", "10000")),
            cors_origins=tuple(x.strip() for x in os.getenv("RUNMONI_CORS_ORIGINS", "http://127.0.0.1:8787,http://localhost:8787").split(",") if x.strip()),
        )


def fail(status, code, message):
    raise HTTPException(status_code=status, detail={"code": code, "message": message})


def token_hash(token):
    return hashlib.sha256(token.encode()).hexdigest()


def distance(a, b):
    lat1, lat2 = math.radians(a["latitude"]), math.radians(b["latitude"])
    delta_lat = lat2 - lat1
    delta_lon = math.radians(b["longitude"] - a["longitude"])
    v = math.sin(delta_lat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lon / 2) ** 2
    return 6371000 * 2 * math.atan2(math.sqrt(v), math.sqrt(max(0, 1 - v)))


def period_bounds(period, anchor_ms):
    dt = datetime.fromtimestamp(anchor_ms / 1000, SEOUL)
    if period == "day":
        start = dt.replace(hour=0, minute=0, second=0, microsecond=0)
        end = start + timedelta(days=1)
    elif period == "month":
        start = dt.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        end = start.replace(year=start.year + 1, month=1) if start.month == 12 else start.replace(month=start.month + 1)
    elif period == "year":
        start = dt.replace(month=1, day=1, hour=0, minute=0, second=0, microsecond=0)
        end = start.replace(year=start.year + 1)
    else:
        return 0, 253402300799000
    return int(start.timestamp() * 1000), int(end.timestamp() * 1000)


def create_app(settings=None, clock=None):
    settings = settings or Settings.from_env()
    now = clock or (lambda: int(time.time() * 1000))
    Path(settings.db_path).parent.mkdir(parents=True, exist_ok=True)

    @contextmanager
    def db(write=False):
        connection = sqlite3.connect(settings.db_path, timeout=15, isolation_level=None)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("PRAGMA busy_timeout=15000")
        try:
            connection.execute("BEGIN IMMEDIATE" if write else "BEGIN")
            yield connection
            connection.commit()
        except BaseException:
            connection.rollback()
            raise
        finally:
            connection.close()

    with sqlite3.connect(settings.db_path) as initial:
        initial.execute("PRAGMA journal_mode=WAL")
        initial.executescript(Path(__file__).with_name("schema.sql").read_text(encoding="utf-8"))
        # Additive pilot migrations: retain history from v0.1.0 without replacing the database.
        additions = {"crews": {"region": "TEXT NOT NULL DEFAULT ''", "pace_label": "TEXT NOT NULL DEFAULT '자유 페이스'"},
                     "parties": {"kind": "TEXT NOT NULL DEFAULT 'local'", "latitude": "REAL", "longitude": "REAL", "pace_label": "TEXT NOT NULL DEFAULT '자유 페이스'"},
                     "track_points": {"segment": "INTEGER NOT NULL DEFAULT 0"}, "runs": {"deleted_at_ms": "INTEGER"}}
        for table, columns in additions.items():
            existing_columns = {row[1] for row in initial.execute(f"PRAGMA table_info({table})")}
            for column, declaration in columns.items():
                if column not in existing_columns:
                    initial.execute(f"ALTER TABLE {table} ADD COLUMN {column} {declaration}")
        previous_presence_sql = initial.execute("SELECT sql FROM sqlite_master WHERE name='presence'").fetchone()[0]
        if "'pilot'" not in previous_presence_sql:
            # Presence is ephemeral; clearing it during the privacy schema upgrade is intentional.
            initial.execute("DROP TABLE presence")
            initial.execute("CREATE TABLE presence (user_id TEXT PRIMARY KEY REFERENCES users(id),run_id TEXT NOT NULL REFERENCES runs(id),scope_type TEXT NOT NULL CHECK(scope_type IN ('crew','party','pilot')),scope_id TEXT NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,updated_at_ms INTEGER NOT NULL,expires_at_ms INTEGER NOT NULL)")
            initial.execute("CREATE INDEX presence_scope_idx ON presence(scope_type,scope_id,expires_at_ms)")
        initial.execute("PRAGMA user_version=2")

    app = FastAPI(title="런모니 초대 코드 파일럿", version=VERSION)
    app.state.settings, app.state.db, app.state.clock = settings, db, now
    app.add_middleware(CORSMiddleware, allow_origins=list(settings.cors_origins), allow_credentials=False,
                       allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE"], allow_headers=["Authorization", "Content-Type"])

    @app.exception_handler(HTTPException)
    async def api_error(request, exc):
        error = exc.detail if isinstance(exc.detail, dict) else {"code": "request_failed", "message": str(exc.detail)}
        return JSONResponse({"error": error}, status_code=exc.status_code)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, exc):
        # Do not echo input bodies: auth invite codes and locations are sensitive.
        return JSONResponse({"error": {"code": "invalid_request", "message": "요청 필드와 형식을 확인해 주세요."}}, status_code=422)

    def current_user(authorization: str | None = Header(default=None)):
        if not authorization or not authorization.startswith("Bearer "):
            fail(401, "authentication_required", "로그인이 필요합니다.")
        with db() as c:
            row = c.execute("SELECT u.* FROM users u JOIN sessions s ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at_ms>?", (token_hash(authorization[7:]), now())).fetchone()
        if row is None:
            fail(401, "invalid_session", "세션이 만료되었거나 유효하지 않습니다.")
        return dict(row)

    def mileage(c, user_id):
        return c.execute("SELECT COALESCE(SUM(delta),0) FROM ledger WHERE user_id=?", (user_id,)).fetchone()[0]

    def profile(c, user_id):
        row = c.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        style = json.loads(row["avatar"])
        return {"id": row["id"], "nickname": row["nickname"], "mileage": mileage(c, user_id), "avatar": canonical_avatar(style),
                "owned_items": migrated_owned(r[0] for r in c.execute("SELECT item_id FROM owned_items WHERE user_id=? ORDER BY item_id", (user_id,)))}

    def crew_member(c, crew_id, user_id):
        role = c.execute("SELECT role FROM crew_members WHERE crew_id=? AND user_id=?", (crew_id, user_id)).fetchone()
        if role is None:
            fail(403, "crew_membership_required", "가입한 크루만 이용할 수 있습니다.")
        return role[0]

    def party_access(c, party_id, user_id, joined=True):
        row = c.execute("SELECT * FROM parties WHERE id=?", (party_id,)).fetchone()
        if row is None:
            fail(404, "party_not_found", "파티를 찾을 수 없습니다.")
        role = crew_member(c, row["crew_id"], user_id)
        if joined and c.execute("SELECT 1 FROM party_members WHERE party_id=? AND user_id=?", (party_id, user_id)).fetchone() is None:
            fail(403, "party_membership_required", "참가한 파티만 이용할 수 있습니다.")
        return row, role

    def scope_access(c, scope_type, scope_id, user_id, active=False):
        if scope_type == "pilot":
            return "member", "pilot"
        if scope_type == "crew":
            return crew_member(c, scope_id, user_id), scope_id
        party, role = party_access(c, scope_id, user_id)
        if active and party["status"] == "cancelled":
            fail(409, "party_cancelled", "취소된 파티입니다.")
        return role, party["crew_id"]

    def blocked(c, a, b):
        return c.execute("SELECT 1 FROM blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)", (a, b, b, a)).fetchone() is not None

    def crew_view(c, crew_id, user_id, discover=False):
        row = dict(c.execute("SELECT * FROM crews WHERE id=?", (crew_id,)).fetchone())
        row["member_count"] = c.execute("SELECT COUNT(*) FROM crew_members WHERE crew_id=?", (crew_id,)).fetchone()[0]
        role = c.execute("SELECT role FROM crew_members WHERE crew_id=? AND user_id=?", (crew_id, user_id)).fetchone()
        row["role"] = role[0] if role else None
        if discover or role is None:
            row.pop("invitation_code")
        return row

    def party_view(c, party_id, user_id):
        row = dict(c.execute("SELECT * FROM parties WHERE id=?", (party_id,)).fetchone())
        row["participant_count"] = c.execute("SELECT COUNT(*) FROM party_members WHERE party_id=?", (party_id,)).fetchone()[0]
        row["is_joined"] = c.execute("SELECT 1 FROM party_members WHERE party_id=? AND user_id=?", (party_id, user_id)).fetchone() is not None
        return row

    def own_run(c, run_id, user_id):
        row = c.execute("SELECT * FROM runs WHERE id=? AND user_id=? AND deleted_at_ms IS NULL", (run_id, user_id)).fetchone()
        if row is None:
            fail(404, "run_not_found", "본인의 러닝 기록만 조회할 수 있습니다.")
        return row

    login_attempts = defaultdict(deque)
    login_lock = threading.Lock()

    @app.get("/health")
    def health():
        voice = bool(settings.livekit_url and settings.livekit_api_key and settings.livekit_api_secret)
        return {"status": "ok", "version": VERSION, "auth_mode": "invite_code_pilot", "capabilities": {
            "voice": voice, "music_broadcast": bool(voice and settings.bridge_url and settings.bridge_secret and settings.bridge_control_secret)}}

    @app.post("/v1/auth/pilot")
    def login(body: PilotLogin, request: Request):
        address = request.client.host if request.client else "unknown"
        with login_lock:
            attempts = login_attempts[address]
            while attempts and attempts[0] < now() - 60000:
                attempts.popleft()
            if len(attempts) >= 10:
                fail(429, "rate_limited", "잠시 후 다시 시도해 주세요.")
            attempts.append(now())
        if not settings.pilot_invite:
            fail(503, "pilot_not_configured", "파일럿 초대 코드가 설정되지 않았습니다.")
        if not body.adult_confirmed:
            fail(403, "adult_only_pilot", "이번 파일럿은 성인만 참가할 수 있습니다.")
        if not hmac.compare_digest(token_hash(body.invite_code), token_hash(settings.pilot_invite)):
            fail(403, "invalid_invite", "초대 코드를 확인해 주세요.")
        user_id, token, expiry = str(uuid4()), secrets.token_urlsafe(32), now() + 30 * 86400000
        with db(True) as c:
            try:
                c.execute("INSERT INTO users(id,nickname,adult_confirmed,created_at_ms) VALUES(?,?,1,?)", (user_id, body.nickname, now()))
            except sqlite3.IntegrityError:
                fail(400, "nickname_taken", "이미 사용 중인 닉네임입니다. 기존 세션을 사용해 주세요.")
            c.execute("INSERT INTO sessions VALUES(?,?,?)", (token_hash(token), user_id, expiry))
            result = profile(c, user_id)
        LOG.info("pilot_account_created user_id=%s auth_mode=invite_code_pilot", user_id)
        return {"access_token": token, "token_type": "bearer", "expires_at_ms": expiry, "user": result}

    @app.delete("/v1/auth/session")
    def logout(user: dict = Depends(current_user), authorization: str = Header()):
        with db(True) as c:
            c.execute("DELETE FROM sessions WHERE token_hash=?", (token_hash(authorization[7:]),))
            c.execute("DELETE FROM presence WHERE user_id=?", (user["id"],))
            c.execute("UPDATE music_leases SET revoked=1 WHERE user_id=?", (user["id"],))
        return {"revoked": True}

    @app.get("/v1/me")
    def me(user: dict = Depends(current_user)):
        with db() as c:
            return profile(c, user["id"])

    @app.patch("/v1/me")
    def patch_me(body: ProfilePatch, user: dict = Depends(current_user)):
        with db(True) as c:
            if body.avatar:
                changes = body.avatar.model_dump(exclude_unset=True)
                owned = set(profile(c, user["id"])["owned_items"])
                for slot, variant in changes.items():
                    if variant is None:
                        fail(422, "invalid_avatar", "아이템 번호를 선택해 주세요.")
                    if f"{slot}:{variant}" not in owned:
                        fail(403, "item_not_owned", "보유한 꾸미기 아이템만 착용할 수 있습니다.")
                # An older client that sends only five slots must preserve new equipment.
                saved = c.execute("SELECT avatar FROM users WHERE id=?", (user["id"],)).fetchone()
                style = canonical_avatar(json.loads(saved["avatar"]))
                if "accessory" in changes:
                    if "headwear" not in changes:
                        changes["headwear"] = {1: 3, 2: 1}.get(changes["accessory"], 0)
                    if "ear" not in changes:
                        changes["ear"] = 1 if changes["accessory"] == 3 else 0
                if "costume" not in changes and costume_covers(style["costume"]) & changes.keys():
                    changes["costume"] = 0
                style.update(changes)
                c.execute("UPDATE users SET avatar=? WHERE id=?", (json.dumps(style), user["id"]))
            if body.nickname:
                try:
                    c.execute("UPDATE users SET nickname=? WHERE id=?", (body.nickname, user["id"]))
                except sqlite3.IntegrityError:
                    fail(400, "nickname_taken", "이미 사용 중인 닉네임입니다.")
            return profile(c, user["id"])

    @app.get("/v1/shop/items")
    def shop(user: dict = Depends(current_user)):
        return {"items": CATALOGUE}

    @app.post("/v1/shop/purchase")
    def purchase(body: ItemPurchase, user: dict = Depends(current_user)):
        item = next((item for item in CATALOGUE if item["id"] == body.item_id), None)
        if item is None:
            fail(404, "item_not_found", "아이템을 찾을 수 없습니다.")
        with db(True) as c:
            already = item["id"] in profile(c, user["id"])["owned_items"]
            if not already:
                if mileage(c, user["id"]) < item["cost"]:
                    fail(409, "insufficient_mileage", "마일리지가 부족합니다.")
                c.execute("INSERT INTO owned_items VALUES(?,?)", (user["id"], item["id"]))
                c.execute("INSERT INTO ledger VALUES(?,?,NULL,?,?,?)", (str(uuid4()), user["id"], item["id"], -item["cost"], now()))
            return {"item_id": item["id"], "mileage": mileage(c, user["id"]), "already_owned": already}

    @app.post("/v1/blocks")
    def block(body: UserBlock, user: dict = Depends(current_user)):
        target = str(body.user_id)
        with db(True) as c:
            if target == user["id"]:
                fail(422, "cannot_block_self", "본인은 차단할 수 없습니다.")
            if c.execute("SELECT 1 FROM users WHERE id=?", (target,)).fetchone() is None:
                fail(404, "user_not_found", "사용자를 찾을 수 없습니다.")
            c.execute("INSERT OR IGNORE INTO blocks VALUES(?,?)", (user["id"], target))
        return {"blocked": True}

    @app.delete("/v1/blocks/{user_id}")
    def unblock(user_id: UUID, user: dict = Depends(current_user)):
        with db(True) as c:
            c.execute("DELETE FROM blocks WHERE blocker_id=? AND blocked_id=?", (user["id"], str(user_id)))
        return {"blocked": False}

    @app.post("/v1/crews")
    def create_crew(body: CrewCreate, user: dict = Depends(current_user)):
        crew_id = str(uuid4())
        with db(True) as c:
            c.execute("INSERT INTO crews(id,name,description,owner_id,invitation_code,created_at_ms,region,pace_label) VALUES(?,?,?,?,?,?,?,?)", (crew_id, body.name, body.description, user["id"], secrets.token_urlsafe(9), now(), body.region, body.pace_label))
            c.execute("INSERT INTO crew_members VALUES(?,?,'owner')", (crew_id, user["id"]))
            return crew_view(c, crew_id, user["id"])

    @app.get("/v1/crews")
    def crews(user: dict = Depends(current_user)):
        with db() as c:
            ids = c.execute("SELECT crew_id FROM crew_members WHERE user_id=? ORDER BY crew_id", (user["id"],)).fetchall()
            return {"items": [crew_view(c, row[0], user["id"]) for row in ids]}

    @app.get("/v1/crews/discover")
    def discover_crews(user: dict = Depends(current_user)):
        with db() as c:
            return {"items": [crew_view(c, row[0], user["id"], True) for row in c.execute("SELECT id FROM crews ORDER BY created_at_ms DESC LIMIT 100").fetchall()]}

    @app.post("/v1/crews/join")
    def join_crew(body: CrewJoin, user: dict = Depends(current_user)):
        with db(True) as c:
            row = c.execute("SELECT id FROM crews WHERE invitation_code=?", (body.invitation_code,)).fetchone()
            if row is None:
                fail(404, "invalid_crew_invite", "크루 초대 코드를 확인해 주세요.")
            c.execute("INSERT OR IGNORE INTO crew_members VALUES(?,?,'member')", (row[0], user["id"]))
            return crew_view(c, row[0], user["id"])

    @app.get("/v1/crews/{crew_id}")
    def get_crew(crew_id: UUID, user: dict = Depends(current_user)):
        with db() as c:
            crew_member(c, str(crew_id), user["id"])
            return crew_view(c, str(crew_id), user["id"])

    @app.post("/v1/crews/{crew_id}/join")
    def open_join_crew(crew_id: UUID, user: dict = Depends(current_user)):
        with db(True) as c:
            if c.execute("SELECT 1 FROM crews WHERE id=?", (str(crew_id),)).fetchone() is None:
                fail(404, "crew_not_found", "크루를 찾을 수 없습니다.")
            c.execute("INSERT OR IGNORE INTO crew_members VALUES(?,?,'member')", (str(crew_id), user["id"]))
            return crew_view(c, str(crew_id), user["id"])

    @app.delete("/v1/crews/{crew_id}/join")
    def leave_crew(crew_id: UUID, user: dict = Depends(current_user)):
        with db(True) as c:
            if crew_member(c, str(crew_id), user["id"]) == "owner":
                fail(409, "owner_cannot_leave", "크루장은 파일럿에서 크루를 탈퇴할 수 없습니다.")
            c.execute("DELETE FROM party_members WHERE user_id=? AND party_id IN (SELECT id FROM parties WHERE crew_id=?)", (user["id"], str(crew_id)))
            c.execute("DELETE FROM crew_members WHERE crew_id=? AND user_id=?", (str(crew_id), user["id"]))
            c.execute("DELETE FROM presence WHERE user_id=? AND (scope_type='crew' AND scope_id=? OR scope_type='party' AND scope_id IN (SELECT id FROM parties WHERE crew_id=?))", (user["id"], str(crew_id), str(crew_id)))
            c.execute("UPDATE music_leases SET revoked=1 WHERE user_id=? AND (scope_type='crew' AND scope_id=? OR scope_type='party' AND scope_id IN (SELECT id FROM parties WHERE crew_id=?))", (user["id"], str(crew_id), str(crew_id)))
        return {"left": True}

    @app.get("/v1/crews/{crew_id}/members")
    def crew_members(crew_id: UUID, user: dict = Depends(current_user)):
        with db() as c:
            crew_member(c, str(crew_id), user["id"])
            rows = c.execute("SELECT m.user_id,m.role,u.nickname,u.avatar FROM crew_members m JOIN users u ON u.id=m.user_id WHERE crew_id=?", (str(crew_id),)).fetchall()
            return {"items": [{**dict(row), "avatar": canonical_avatar(json.loads(row["avatar"]))} for row in rows if not blocked(c, user["id"], row["user_id"])]}

    @app.patch("/v1/crews/{crew_id}/members/{user_id}")
    def set_role(crew_id: UUID, user_id: UUID, body: RolePatch, user: dict = Depends(current_user)):
        with db(True) as c:
            if crew_member(c, str(crew_id), user["id"]) != "owner":
                fail(403, "owner_required", "크루장만 역할을 변경할 수 있습니다.")
            old = crew_member(c, str(crew_id), str(user_id))
            if old == "owner":
                fail(409, "owner_role_locked", "크루장 역할은 변경할 수 없습니다.")
            c.execute("UPDATE crew_members SET role=? WHERE crew_id=? AND user_id=?", (body.role, str(crew_id), str(user_id)))
            if body.role == "member":
                c.execute("UPDATE music_leases SET revoked=1 WHERE user_id=? AND (scope_type='crew' AND scope_id=? OR scope_type='party' AND scope_id IN (SELECT id FROM parties WHERE crew_id=?))", (str(user_id), str(crew_id), str(crew_id)))
        return {"user_id": str(user_id), "role": body.role}

    @app.post("/v1/parties")
    def create_party(body: PartyCreate, user: dict = Depends(current_user)):
        if not now() <= body.starts_at_ms <= now() + 366 * 86400000:
            fail(422, "invalid_party_time", "앞으로 1년 이내의 일정을 입력해 주세요.")
        if body.kind == "local" and len(body.meeting_point) < 2:
            fail(422, "meeting_point_required", "현장 파티의 집결지를 입력해 주세요.")
        if (body.latitude is None) != (body.longitude is None):
            fail(422, "coordinate_pair_required", "위도와 경도를 함께 입력해 주세요.")
        party_id = str(uuid4())
        with db(True) as c:
            if crew_member(c, str(body.crew_id), user["id"]) != "owner":
                fail(403, "owner_required", "크루장만 파티를 만들 수 있습니다.")
            c.execute("INSERT INTO parties(id,crew_id,creator_id,title,starts_at_ms,meeting_point,distance_km,capacity,kind,latitude,longitude,pace_label) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", (party_id, str(body.crew_id), user["id"], body.title, body.starts_at_ms, body.meeting_point, body.distance_km, body.capacity, body.kind, body.latitude, body.longitude, body.pace_label))
            c.execute("INSERT INTO party_members VALUES(?,?)", (party_id, user["id"]))
            return party_view(c, party_id, user["id"])

    @app.get("/v1/parties")
    def parties(user: dict = Depends(current_user)):
        with db() as c:
            ids = c.execute("SELECT p.id FROM parties p JOIN crew_members m ON p.crew_id=m.crew_id WHERE m.user_id=? AND p.status='scheduled' ORDER BY starts_at_ms LIMIT 100", (user["id"],)).fetchall()
            return {"items": [party_view(c, row[0], user["id"]) for row in ids]}

    @app.get("/v1/parties/{party_id}")
    def get_party(party_id: UUID, user: dict = Depends(current_user)):
        with db() as c:
            party_access(c, str(party_id), user["id"], False)
            return party_view(c, str(party_id), user["id"])

    @app.post("/v1/parties/{party_id}/join")
    def join_party(party_id: UUID, user: dict = Depends(current_user)):
        with db(True) as c:
            party, _ = party_access(c, str(party_id), user["id"], False)
            if party["status"] == "cancelled":
                fail(409, "party_cancelled", "취소된 파티입니다.")
            already = c.execute("SELECT 1 FROM party_members WHERE party_id=? AND user_id=?", (str(party_id), user["id"])).fetchone()
            if not already:
                if c.execute("SELECT COUNT(*) FROM party_members WHERE party_id=?", (str(party_id),)).fetchone()[0] >= party["capacity"]:
                    fail(409, "party_full", "파티 정원이 마감되었습니다.")
                c.execute("INSERT INTO party_members VALUES(?,?)", (str(party_id), user["id"]))
            return party_view(c, str(party_id), user["id"])

    @app.delete("/v1/parties/{party_id}/join")
    def leave_party(party_id: UUID, user: dict = Depends(current_user)):
        with db(True) as c:
            party, _ = party_access(c, str(party_id), user["id"], False)
            if party["creator_id"] == user["id"]:
                fail(409, "creator_cannot_leave", "주최자는 파티를 취소해 주세요.")
            c.execute("DELETE FROM party_members WHERE party_id=? AND user_id=?", (str(party_id), user["id"]))
            c.execute("DELETE FROM presence WHERE scope_type='party' AND scope_id=? AND user_id=?", (str(party_id), user["id"]))
            c.execute("UPDATE music_leases SET revoked=1 WHERE scope_type='party' AND scope_id=? AND user_id=?", (str(party_id), user["id"]))
            return party_view(c, str(party_id), user["id"])

    @app.post("/v1/parties/{party_id}/cancel")
    def cancel_party(party_id: UUID, user: dict = Depends(current_user)):
        with db(True) as c:
            party, role = party_access(c, str(party_id), user["id"], False)
            if role != "owner" and party["creator_id"] != user["id"]:
                fail(403, "owner_required", "주최자만 파티를 취소할 수 있습니다.")
            c.execute("UPDATE parties SET status='cancelled' WHERE id=?", (str(party_id),))
            c.execute("DELETE FROM presence WHERE scope_type='party' AND scope_id=?", (str(party_id),))
            c.execute("UPDATE music_leases SET revoked=1 WHERE scope_type='party' AND scope_id=?", (str(party_id),))
            return party_view(c, str(party_id), user["id"])

    def list_messages(scope_type, scope_id, user, after_id, limit):
        with db() as c:
            scope_access(c, scope_type, scope_id, user["id"])
            order = "DESC" if after_id == 0 else "ASC"
            rows = c.execute(f"SELECT m.*,u.nickname FROM messages m JOIN users u ON u.id=m.sender_id WHERE scope_type=? AND scope_id=? AND m.id>? AND NOT EXISTS(SELECT 1 FROM blocks b WHERE b.blocker_id=m.sender_id AND b.blocked_id=? OR b.blocker_id=? AND b.blocked_id=m.sender_id) ORDER BY m.id {order} LIMIT ?", (scope_type, scope_id, after_id, user["id"], user["id"], limit)).fetchall()
            if after_id == 0:
                rows = rows[::-1]
            return {"items": [dict(row) for row in rows]}

    def send_message(scope_type, scope_id, body, user):
        with db(True) as c:
            role, _ = scope_access(c, scope_type, scope_id, user["id"], True)
            if body.kind == "announcement" and role != "owner":
                party_owner = scope_type == "party" and c.execute("SELECT creator_id FROM parties WHERE id=?", (scope_id,)).fetchone()[0] == user["id"]
                if not party_owner:
                    fail(403, "announcement_permission_required", "운영자만 공지를 작성할 수 있습니다.")
            cursor = c.execute("INSERT INTO messages(scope_type,scope_id,sender_id,body,kind,created_at_ms) VALUES(?,?,?,?,?,?)", (scope_type, scope_id, user["id"], body.body, body.kind, now()))
            return dict(c.execute("SELECT m.*,u.nickname FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.id=?", (cursor.lastrowid,)).fetchone())

    @app.get("/v1/crews/{crew_id}/messages")
    def crew_messages(crew_id: UUID, after_id: int = Query(0, ge=0), limit: int = Query(100, ge=1, le=100), user: dict = Depends(current_user)):
        return list_messages("crew", str(crew_id), user, after_id, limit)

    @app.post("/v1/crews/{crew_id}/messages")
    def post_crew_message(crew_id: UUID, body: MessageCreate, user: dict = Depends(current_user)):
        return send_message("crew", str(crew_id), body, user)

    @app.get("/v1/parties/{party_id}/messages")
    def party_messages(party_id: UUID, after_id: int = Query(0, ge=0), limit: int = Query(100, ge=1, le=100), user: dict = Depends(current_user)):
        return list_messages("party", str(party_id), user, after_id, limit)

    @app.post("/v1/parties/{party_id}/messages")
    def post_party_message(party_id: UUID, body: MessageCreate, user: dict = Depends(current_user)):
        return send_message("party", str(party_id), body, user)

    @app.post("/v1/runs")
    def start_run(body: RunCreate, user: dict = Depends(current_user)):
        if body.started_at_ms > now() + 60000 or body.started_at_ms < now() - 366 * 86400000:
            fail(422, "invalid_run_time", "러닝 시작 시간을 확인해 주세요.")
        run_id = str(body.id or uuid4())
        with db(True) as c:
            existing = c.execute("SELECT * FROM runs WHERE id=?", (run_id,)).fetchone()
            if existing:
                if existing["user_id"] != user["id"]:
                    fail(409, "run_id_conflict", "다른 기록 ID를 사용해 주세요.")
                if existing["deleted_at_ms"] is not None:
                    fail(409, "run_deleted", "삭제된 기록 ID는 다시 사용할 수 없습니다.")
                if existing["started_at_ms"] != body.started_at_ms or existing["party_id"] != (str(body.party_id) if body.party_id else None):
                    fail(409, "run_id_conflict", "기존 러닝 시작 정보와 다릅니다.")
                return dict(existing)
            if body.party_id:
                scope_access(c, "party", str(body.party_id), user["id"], True)
            c.execute("INSERT INTO runs(id,user_id,party_id,started_at_ms) VALUES(?,?,?,?)", (run_id, user["id"], str(body.party_id) if body.party_id else None, body.started_at_ms))
            return dict(own_run(c, run_id, user["id"]))

    @app.post("/v1/runs/{run_id}/tracks/batch")
    def track_batch(run_id: UUID, body: TrackBatch, user: dict = Depends(current_user)):
        accepted, duplicates = 0, 0
        with db(True) as c:
            run = own_run(c, str(run_id), user["id"])
            if run["status"] != "active":
                fail(409, "run_finalized", "완료된 러닝에는 GPS를 추가할 수 없습니다.")
            for point in body.points:
                if point.recorded_at_ms < run["started_at_ms"] or point.recorded_at_ms > now() + 60000:
                    fail(422, "invalid_point_time", "GPS 측정 시간을 확인해 주세요.")
                values = (str(run_id), point.seq, point.latitude, point.longitude, point.recorded_at_ms, point.accuracy_m, point.altitude_m, point.segment)
                previous = c.execute("SELECT * FROM track_points WHERE run_id=? AND seq=?", (str(run_id), point.seq)).fetchone()
                if previous:
                    if tuple(previous) != values:
                        fail(409, "sequence_conflict", "같은 GPS 순번에 다른 값이 있습니다.")
                    duplicates += 1
                else:
                    c.execute("INSERT INTO track_points(run_id,seq,latitude,longitude,recorded_at_ms,accuracy_m,altitude_m,segment) VALUES(?,?,?,?,?,?,?,?)", values)
                    accepted += 1
            total = c.execute("SELECT COUNT(*) FROM track_points WHERE run_id=?", (str(run_id),)).fetchone()[0]
        return {"accepted": accepted, "duplicates": duplicates, "total_points": total}

    @app.post("/v1/runs/{run_id}/finalize")
    def finalize(run_id: UUID, body: RunFinalize, user: dict = Depends(current_user)):
        with db(True) as c:
            run = own_run(c, str(run_id), user["id"])
            if run["status"] == "finalized":
                return dict(run)
            elapsed = (body.ended_at_ms - run["started_at_ms"]) / 1000.0
            if elapsed < 0 or elapsed > 86400 or body.active_duration_s > elapsed or body.ended_at_ms > now() + 60000:
                fail(422, "invalid_duration", "러닝 시간을 확인해 주세요.")
            points = c.execute("SELECT * FROM track_points WHERE run_id=? ORDER BY seq", (str(run_id),)).fetchall()
            metres = 0.0
            previous = None
            for expected, point in enumerate(points):
                if point["seq"] != expected:
                    fail(409, "missing_track_sequence", "누락된 GPS 순번을 먼저 업로드해 주세요.")
                if point["recorded_at_ms"] > body.ended_at_ms:
                    fail(422, "point_after_finish", "종료 이후 GPS가 포함되어 있습니다.")
                if previous:
                    gap = (point["recorded_at_ms"] - previous["recorded_at_ms"]) / 1000
                    if gap <= 0:
                        fail(409, "non_monotonic_track", "GPS 측정 시간이 순서대로 증가해야 합니다.")
                    segment = distance(previous, point)
                    if point["segment"] == previous["segment"] and gap <= 60 and point["accuracy_m"] <= 30 and previous["accuracy_m"] <= 30 and segment / gap <= 8:
                        metres += segment
                previous = point
            day_start, day_end = period_bounds("day", body.ended_at_ms)
            earned_today = c.execute("SELECT COALESCE(SUM(delta),0) FROM ledger WHERE user_id=? AND run_id IS NOT NULL AND created_at_ms>=? AND created_at_ms<?", (user["id"], day_start, day_end)).fetchone()[0]
            reward = min(max(0, settings.reward_daily_limit - earned_today), int(metres // 10))
            c.execute("UPDATE runs SET status='finalized',ended_at_ms=?,distance_m=?,active_duration_s=?,elapsed_duration_s=?,reward_points=? WHERE id=?", (body.ended_at_ms, round(metres, 2), body.active_duration_s, elapsed, reward, str(run_id)))
            c.execute("INSERT INTO ledger VALUES(?,?,?,NULL,?,?)", (str(uuid4()), user["id"], str(run_id), reward, body.ended_at_ms))
            c.execute("DELETE FROM presence WHERE run_id=?", (str(run_id),))
            result = dict(own_run(c, str(run_id), user["id"]))
        LOG.info("run_finalized run_id=%s reward_points=%d", str(run_id), reward)
        return result

    @app.get("/v1/runs")
    def runs(user: dict = Depends(current_user)):
        with db() as c:
            return {"items": [dict(row) for row in c.execute("SELECT * FROM runs WHERE user_id=? AND deleted_at_ms IS NULL ORDER BY started_at_ms DESC LIMIT 500", (user["id"],))]}

    @app.delete("/v1/runs/{run_id}")
    def delete_run(run_id: UUID, user: dict = Depends(current_user)):
        with db(True) as c:
            row = c.execute("SELECT id FROM runs WHERE id=? AND user_id=?", (str(run_id), user["id"])).fetchone()
            if row is None:
                fail(404, "run_not_found", "본인의 러닝 기록만 삭제할 수 있습니다.")
            c.execute("UPDATE runs SET deleted_at_ms=COALESCE(deleted_at_ms,?) WHERE id=?", (now(), str(run_id)))
            c.execute("DELETE FROM presence WHERE run_id=?", (str(run_id),))
            c.execute("DELETE FROM track_points WHERE run_id=?", (str(run_id),))
        return {"deleted": True}

    @app.get("/v1/runs/{run_id}")
    def get_run(run_id: UUID, user: dict = Depends(current_user)):
        with db() as c:
            return dict(own_run(c, str(run_id), user["id"]))

    @app.get("/v1/runs/{run_id}/tracks")
    def get_track(run_id: UUID, user: dict = Depends(current_user)):
        with db() as c:
            own_run(c, str(run_id), user["id"])
            return {"items": [dict(row) for row in c.execute("SELECT * FROM track_points WHERE run_id=? ORDER BY seq", (str(run_id),))]}

    @app.get("/v1/stats")
    def stats(period: Literal["day", "month", "year", "all"] = "month", anchor_ms: int | None = Query(default=None, ge=1, le=253402300798999), user: dict = Depends(current_user)):
        start, end = period_bounds(period, anchor_ms or now())
        with db() as c:
            row = c.execute("SELECT COALESCE(SUM(distance_m),0) distance_m,COALESCE(SUM(active_duration_s),0) active_duration_s,COUNT(*) run_count FROM runs WHERE user_id=? AND status='finalized' AND deleted_at_ms IS NULL AND started_at_ms>=? AND started_at_ms<?", (user["id"], start, end)).fetchone()
            return {"period": period, **dict(row), "mileage": mileage(c, user["id"])}

    @app.put("/v1/presence")
    def put_presence(body: PresenceUpdate, user: dict = Depends(current_user)):
        with db(True) as c:
            if not body.exact_opt_in:
                c.execute("DELETE FROM presence WHERE user_id=?", (user["id"],))
                return {"shared": False}
            run = own_run(c, str(body.run_id), user["id"])
            if run["status"] != "active":
                fail(409, "run_finalized", "러닝 중에만 위치를 공유할 수 있습니다.")
            if body.scope_type != "pilot" and body.scope_id is None:
                fail(422, "scope_id_required", "크루 또는 파티 ID가 필요합니다.")
            scope_access(c, body.scope_type, str(body.scope_id), user["id"], True)
            scope_id = "pilot" if body.scope_type == "pilot" else str(body.scope_id)
            stamp = now()
            c.execute("INSERT INTO presence VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET run_id=excluded.run_id,scope_type=excluded.scope_type,scope_id=excluded.scope_id,latitude=excluded.latitude,longitude=excluded.longitude,updated_at_ms=excluded.updated_at_ms,expires_at_ms=excluded.expires_at_ms", (user["id"], str(body.run_id), body.scope_type, scope_id, body.latitude, body.longitude, stamp, stamp + 45000))
        return {"shared": True, "updated_at_ms": stamp, "expires_at_ms": stamp + 45000}

    @app.get("/v1/presence")
    def get_presence(scope_type: Literal["crew", "party", "pilot"], scope_id: UUID | None = None, user: dict = Depends(current_user)):
        if scope_type != "pilot" and scope_id is None:
            fail(422, "scope_id_required", "크루 또는 파티 ID가 필요합니다.")
        scope_key = "pilot" if scope_type == "pilot" else str(scope_id)
        with db() as c:
            scope_access(c, scope_type, str(scope_id), user["id"], True)
            rows = c.execute("SELECT p.user_id,u.nickname,u.avatar,p.latitude,p.longitude,p.updated_at_ms,p.expires_at_ms,r.party_id FROM presence p JOIN users u ON u.id=p.user_id JOIN runs r ON r.id=p.run_id WHERE p.scope_type=? AND p.scope_id=? AND p.expires_at_ms>? AND r.status='active' AND r.deleted_at_ms IS NULL", (scope_type, scope_key, now())).fetchall()
            return {"items": [{**dict(row), "avatar": canonical_avatar(json.loads(row["avatar"]))} for row in rows if not blocked(c, user["id"], row["user_id"])]}

    @app.delete("/v1/presence")
    def clear_presence(user: dict = Depends(current_user)):
        with db(True) as c:
            c.execute("DELETE FROM presence WHERE user_id=?", (user["id"],))
        return {"shared": False}

    def room_permission(c, body, user, dj=False):
        role, crew_id = scope_access(c, body.scope_type, str(body.scope_id), user["id"], True)
        if dj and role not in ("owner", "dj"):
            fail(403, "dj_permission_required", "크루장 또는 DJ만 방송할 수 있습니다.")
        table, column = ("crew_members", "crew_id") if body.scope_type == "crew" else ("party_members", "party_id")
        members = c.execute(f"SELECT user_id FROM {table} WHERE {column}=?", (str(body.scope_id),)).fetchall()
        if any(blocked(c, user["id"], row[0]) for row in members):
            fail(403, "blocked_room_member", "차단한 사용자와 같은 음성방에 참가할 수 없습니다.")
        return f"{body.scope_type}-{body.scope_id}", crew_id

    def require_audio(music=False):
        ready = settings.livekit_url and settings.livekit_api_key and settings.livekit_api_secret
        if music:
            ready = ready and settings.bridge_url and settings.bridge_secret and settings.bridge_control_secret
        if not ready:
            fail(503, "capability_not_configured", "음성 또는 음악 방송 서버가 아직 설정되지 않았습니다.")

    @app.post("/v1/voice/token")
    def voice_token(body: RoomRequest, user: dict = Depends(current_user)):
        with db() as c:
            room, _ = room_permission(c, body, user)
        require_audio()
        from livekit import api
        token = (api.AccessToken(settings.livekit_api_key, settings.livekit_api_secret)
                 .with_identity(user["id"]).with_name(user["nickname"]).with_ttl(timedelta(minutes=5))
                 .with_grants(api.VideoGrants(room_join=True, room=room, can_publish=True,
                                              can_subscribe=True, can_publish_data=False,
                                              can_publish_sources=["microphone"])).to_jwt())
        return {"server_url": settings.livekit_url, "token": token, "room_name": room, "expires_at_ms": now() + 300000}

    @app.post("/v1/music/lease")
    def music_lease(body: MusicLease, user: dict = Depends(current_user)):
        if not body.pilot_test_acknowledged:
            fail(403, "pilot_test_acknowledgement_required", "지인 파일럿 기술 테스트임을 확인해 주세요.")
        with db(True) as c:
            room, _ = room_permission(c, body, user, True)
            require_audio(True)
            stamp = now()
            if c.execute("SELECT 1 FROM music_leases WHERE scope_type=? AND scope_id=? AND revoked=0 AND expires_at_ms>?", (body.scope_type, str(body.scope_id), stamp)).fetchone():
                fail(409, "dj_lease_held", "다른 방송이 진행 중입니다. 이전 방송을 종료해 주세요.")
            lease_id, session_id, expiry = str(uuid4()), str(body.session_id or uuid4()), stamp + 900000
            c.execute("INSERT INTO music_leases VALUES(?,?,?,?,?,?,0)", (lease_id, user["id"], body.scope_type, str(body.scope_id), session_id, expiry))
            import jwt
            token = jwt.encode({"sub": user["id"], "room": room, "session_id": session_id, "lease_id": lease_id, "role": "dj", "iat": stamp // 1000, "exp": expiry // 1000}, settings.bridge_secret, algorithm="HS256")
            return {"bridge_url": settings.bridge_url, "token": token, "room_name": room, "session_id": session_id, "lease_id": lease_id, "expires_at_ms": expiry}

    @app.delete("/v1/music/lease/{lease_id}")
    def revoke_lease(lease_id: UUID, user: dict = Depends(current_user)):
        with db(True) as c:
            lease = c.execute("SELECT * FROM music_leases WHERE id=?", (str(lease_id),)).fetchone()
            if lease is None:
                fail(404, "lease_not_found", "방송을 찾을 수 없습니다.")
            role, _ = scope_access(c, lease["scope_type"], lease["scope_id"], user["id"])
            if lease["user_id"] != user["id"] and role != "owner":
                fail(403, "lease_holder_required", "방송자 또는 크루장만 방송을 종료할 수 있습니다.")
            c.execute("UPDATE music_leases SET revoked=1 WHERE id=?", (str(lease_id),))
        return {"revoked": True}

    @app.get("/v1/music/lease/{lease_id}/status")
    def lease_status(lease_id: UUID, authorization: str | None = Header(default=None)):
        expected = f"Bearer {settings.bridge_control_secret}"
        if not settings.bridge_control_secret or not authorization or not hmac.compare_digest(token_hash(authorization), token_hash(expected)):
            fail(401, "bridge_authentication_required", "방송 서버 인증이 필요합니다.")
        with db() as c:
            lease = c.execute("SELECT * FROM music_leases WHERE id=?", (str(lease_id),)).fetchone()
            if lease is None:
                return {"active": False, "expires_at_ms": 0}
            active = not lease["revoked"] and lease["expires_at_ms"] > now()
            if active:
                try:
                    body = RoomRequest(scope_type=lease["scope_type"], scope_id=lease["scope_id"])
                    holder = {"id": lease["user_id"]}
                    room_permission(c, body, holder, True)
                except HTTPException:
                    active = False
            return {"active": bool(active), "expires_at_ms": lease["expires_at_ms"]}

    return app


app = create_app()
