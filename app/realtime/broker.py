"""Redis broker helpers for realtime Pub/Sub."""

from __future__ import annotations

import asyncio
import json
import os
from typing import Any, Awaitable, Callable, Dict, Optional

try:
    import redis.asyncio as redis_async
    from redis import Redis
except Exception:  # pragma: no cover
    redis_async = None  # type: ignore[assignment]
    Redis = None  # type: ignore[assignment]


DEFAULT_CHANNEL = "map_updates"


def get_redis_url() -> str:
    """Return REDIS_URL from Flask config or environment."""
    try:
        from flask import current_app

        url = (current_app.config.get("REDIS_URL") or "").strip()
        if url:
            return url
    except Exception:
        pass
    return (os.getenv("REDIS_URL") or "").strip()


def get_channel() -> str:
    try:
        from flask import current_app

        channel = (current_app.config.get("REALTIME_REDIS_CHANNEL") or "").strip()
        if channel:
            return channel
    except Exception:
        pass
    return (os.getenv("REALTIME_REDIS_CHANNEL") or DEFAULT_CHANNEL).strip() or DEFAULT_CHANNEL


class RedisBroker:
    """Publisher/subscriber broker over Redis Pub/Sub."""

    def __init__(self, redis_url: Optional[str] = None) -> None:
        self.redis_url = (redis_url or get_redis_url()).strip()
        self._sync_client: Optional[Redis] = None

    def publish_event(self, channel: str, payload: Dict[str, Any]) -> bool:
        """Publish raw payload dict into channel."""
        if not self.redis_url or Redis is None:
            return False
        try:
            if self._sync_client is None:
                self._sync_client = Redis.from_url(self.redis_url, decode_responses=True)
            self._sync_client.publish(channel, json.dumps(payload, ensure_ascii=False))
            return True
        except Exception:
            return False

    async def listener(
        self,
        channel: str,
        on_message: Callable[[Dict[str, Any]], Awaitable[None]],
    ) -> None:
        """Listen channel forever and pass decoded payload to callback."""
        if not self.redis_url or redis_async is None:
            return

        redis_conn = redis_async.from_url(self.redis_url, decode_responses=True)
        pubsub = redis_conn.pubsub()
        await pubsub.subscribe(channel)
        try:
            async for msg in pubsub.listen():
                if not msg or msg.get("type") != "message":
                    continue
                raw = msg.get("data")
                if not raw:
                    continue
                try:
                    payload = json.loads(raw)
                except Exception:
                    continue
                if isinstance(payload, dict):
                    await on_message(payload)
        finally:
            try:
                await pubsub.unsubscribe(channel)
            except Exception:
                pass
            try:
                await pubsub.close()
            except Exception:
                pass
            try:
                await redis_conn.close()
            except Exception:
                pass


_broker_singleton: Optional[RedisBroker] = None


def get_broker() -> RedisBroker:
    global _broker_singleton
    if _broker_singleton is None:
        _broker_singleton = RedisBroker()
    return _broker_singleton


def publish(event: str, data: Dict[str, Any]) -> bool:
    """Backward-compatible helper for existing call-sites."""
    payload = {"event": event, "data": data}
    return get_broker().publish_event(get_channel(), payload)


async def subscribe_forever(
    *,
    redis_url: str,
    channel: str,
    on_event: Callable[[str, Dict[str, Any]], Awaitable[None]],
) -> None:
    """Backward-compatible listener: maps payload to ``on_event(event, data)``."""
    broker = RedisBroker(redis_url=redis_url)

    async def _on_payload(payload: Dict[str, Any]) -> None:
        event = payload.get("event")
        data = payload.get("data")
        if isinstance(event, str) and isinstance(data, dict):
            await on_event(event, data)

    await broker.listener(channel, _on_payload)
