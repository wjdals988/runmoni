from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StrictBool


class Model(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False, str_strip_whitespace=True)


class PilotLogin(Model):
    invite_code: str = Field(min_length=8, max_length=200)
    nickname: str = Field(min_length=2, max_length=24, pattern=r"^[\w가-힣 -]+$")
    adult_confirmed: StrictBool


class GuestLogin(Model):
    nickname: str = Field(min_length=2, max_length=24, pattern=r"^[\w가-힣 -]+$")
    adult_confirmed: StrictBool


class Avatar(Model):
    hair: int = Field(default=0, ge=0, le=3)
    top: int = Field(default=0, ge=0, le=5)
    bottom: int = Field(default=0, ge=0, le=5)
    shoes: int = Field(default=0, ge=0, le=3)
    accessory: int = Field(default=0, ge=0, le=3)
    eyewear: int = Field(default=0, ge=0, le=3)
    wrist: int = Field(default=0, ge=0, le=3)
    waist: int = Field(default=0, ge=0, le=3)
    hydration: int = Field(default=0, ge=0, le=3)
    face: int = Field(default=0, ge=0, le=3)
    socks: int = Field(default=0, ge=0, le=3)
    headwear: int | None = Field(default=None, ge=0, le=3)
    ear: int | None = Field(default=None, ge=0, le=3)
    costume: int = Field(default=0, ge=0, le=5)


class ProfilePatch(Model):
    nickname: str | None = Field(default=None, min_length=2, max_length=24, pattern=r"^[\w가-힣 -]+$")
    avatar: Avatar | None = None


class ItemPurchase(Model):
    item_id: str = Field(min_length=1, max_length=80)


class UserBlock(Model):
    user_id: UUID


class CrewCreate(Model):
    name: str = Field(min_length=2, max_length=60)
    description: str = Field(default="", max_length=500)
    region: str = Field(default="", max_length=80)
    pace_label: str = Field(default="자유 페이스", max_length=80)
    visibility: Literal["pilot", "open"] = "pilot"


class CrewJoin(Model):
    invitation_code: str = Field(min_length=8, max_length=100)


class RolePatch(Model):
    role: Literal["dj", "member"]


class PartyCreate(Model):
    crew_id: UUID | None = None
    title: str = Field(min_length=2, max_length=80)
    starts_at_ms: int = Field(gt=0)
    kind: Literal["local", "remote"] = "local"
    meeting_point: str = Field(default="", max_length=200)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    distance_km: float = Field(gt=0, le=100)
    pace_label: str = Field(default="자유 페이스", min_length=1, max_length=80)
    capacity: int = Field(ge=2, le=20)


class MessageCreate(Model):
    body: str = Field(min_length=1, max_length=2000)
    kind: Literal["chat", "announcement"] = "chat"


class RunCreate(Model):
    id: UUID | None = None
    started_at_ms: int = Field(gt=0)
    party_id: UUID | None = None


class TrackPoint(Model):
    seq: int = Field(ge=0)
    segment: int = Field(default=0, ge=0)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    recorded_at_ms: int = Field(gt=0)
    accuracy_m: float = Field(ge=0, le=10000)
    altitude_m: float | None = Field(default=None, ge=-1000, le=10000)


class TrackBatch(Model):
    points: list[TrackPoint] = Field(min_length=1, max_length=1000)


class RunFinalize(Model):
    ended_at_ms: int = Field(gt=0)
    active_duration_s: float = Field(ge=0, le=86400)


class RoomRequest(Model):
    scope_type: Literal["crew", "party"]
    scope_id: UUID


class PresenceUpdate(Model):
    scope_type: Literal["crew", "party", "pilot"]
    scope_id: UUID | None = None
    run_id: UUID
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    exact_opt_in: StrictBool


class MusicLease(RoomRequest):
    session_id: UUID | None = None
    pilot_test_acknowledged: StrictBool
