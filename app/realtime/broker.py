"""Redis broker helpers for realtime Pub/Sub.

Используется для доставки realtime-событий (chat/pending/duty/tracker/sos)
между несколькими процессами/репликами.

Если REDIS_URL не задан — модуль работает как no-op.
"""

from __future__ import annotations

import json
import os
from typing import Any, Awaitable, Callable, Dict, Optional

# redis-py 5.x содержит asyncio API: redis.asyncio
try:
    import redis.asyncio as redis_async
    from redis import Redis
except Exception:  # pragma: no cover
    redis_async = None  # type: ignore[assignment]
    Redis = None  # type: ignore[assignment]


DEFAULT_CHANNEL = "mapv12:realtime"

_sync_client: Optional["Redis"] = None


def get_redis_url() -> str:
    """Вернуть REDIS_URL из Flask config или env."""
    # Flask может быть не импортируем/не доступен при раннем импорте
    try:
        from flask import current_app  # type: ignore
        url = (current_app.config.get("REDIS_URL") or "").strip()
        if url:
            return url
    except Exception:
        pass
    return (os.getenv("REDIS_URL") or "").strip()


def get_channel() -> str:
    try:
        from flask import current_app  # type: ignore
        ch = (current_app.config.get("REALTIME_REDIS_CHANNEL") or "").strip()
        if ch:
            return ch
    except Exception:
        pass
    return (os.getenv("REALTIME_REDIS_CHANNEL") or DEFAULT_CHANNEL).strip() or DEFAULT_CHANNEL


def publish(event: str, data: Dict[str, Any]) -> bool:
    """Опубликовать событие в Redis Pub/Sub. Возвращает True/False."""
    url = get_redis_url()
    if not url or Redis is None:
        return False

    global _sync_client
    try:
        if _sync_client is None:
            _sync_client = Redis.from_url(url, decode_responses=True)
        payload = json.dumps({"event": event, "data": data}, ensure_ascii=False)
        _sync_client.publish(get_channel(), payload)
        return True
    except Exception:
        return False


async def subscribe_forever(
    *,
    redis_url: str,
    channel: str,
    on_event: Callable[[str, Dict[str, Any]], Awaitable[None]],
) -> None:
    """Подписаться на канал и вызывать on_event на каждое сообщение."""
    if not redis_url or redis_async is None:
        return

    r = redis_async.from_url(redis_url, decode_responses=True)
    pubsub = r.pubsub()
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
                event = payload.get("event")
                data = payload.get("data")
                if isinstance(event, str) and isinstance(data, dict):
                    await on_event(event, data)
            except Exception:
                continue
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
            await r.close()
        except Exception:
            pass
