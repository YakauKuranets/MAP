"""ASGI приложение (HTTP + WebSocket) для продакшена.

Зачем:
  - Gunicorn WSGI не обслуживает WebSocket.
  - Запуск отдельного WS-сервера (port 8765) в проде неудобен и ломает
    рассылку событий при наличии нескольких воркеров.

Решение:
  - Поднимаем Flask (WSGI) как ASGI через WsgiToAsgi.
  - Добавляем нативный ASGI WebSocket роут /ws.

Запуск:
  uvicorn asgi_realtime:app --host 0.0.0.0 --port 8000

Клиент (admin UI) получает токен через /api/realtime/token и подключается
к ws://<host>/ws?token=...
"""

from __future__ import annotations

import asyncio
import os

from env_loader import load_dotenv_like

# Load .env if present (so uvicorn запуск видит TELEGRAM_BOT_TOKEN и др.)
load_dotenv_like()
from urllib.parse import urlparse

from asgiref.wsgi import WsgiToAsgi

from starlette.applications import Starlette
from starlette.routing import Mount, WebSocketRoute
from starlette.websockets import WebSocket, WebSocketDisconnect

from app import create_app
from app.config import DevelopmentConfig, ProductionConfig
from app.realtime.tokens import verify_token
from app.realtime.hub import register, unregister, broadcast
from app.realtime.broker import get_redis_url, get_channel, subscribe_forever

redis_task = None  # asyncio.Task | None


def _select_config_class():
    env = (os.getenv('APP_ENV') or os.getenv('FLASK_ENV') or 'development').lower()
    if env.startswith('prod'):
        return ProductionConfig
    return DevelopmentConfig


flask_app = create_app(_select_config_class())
flask_asgi = WsgiToAsgi(flask_app)


def _origin_allowed(origin: str | None, *, host_header: str | None) -> bool:
    """Origin-check для WebSocket.

    - Если allowlist задан (REALTIME_ALLOWED_ORIGINS) — требуем точное совпадение.
    - Если Origin отсутствует — разрешаем (CLI / локальные клиенты).
    - Иначе разрешаем same-host (по hostname).
    """
    if not origin:
        return True
    allow_raw = str(flask_app.config.get('REALTIME_ALLOWED_ORIGINS', '') or '').strip()
    allow = [o.strip() for o in allow_raw.split(',') if o.strip()]
    if allow:
        return origin in allow
    try:
        origin_host = urlparse(origin).hostname
    except Exception:
        return False
    if not origin_host:
        return False
    ws_host = (host_header or '').split(':')[0]
    if ws_host:
        return origin_host == ws_host
    # fallback (dev)
    return origin_host in {'localhost', '127.0.0.1'}


async def ws_endpoint(websocket: WebSocket) -> None:
    origin = websocket.headers.get('origin')
    if not _origin_allowed(origin, host_header=websocket.headers.get('host')):
        await websocket.close(code=1008)
        return

    token = websocket.query_params.get('token')
    ttl = int(flask_app.config.get('REALTIME_TOKEN_TTL_SEC', 600))
    payload = verify_token(flask_app.secret_key, token or '', max_age=ttl) if token else None
    if not payload:
        await websocket.close(code=1008)
        return

    await websocket.accept()
    await register(websocket)
    try:
        while True:
            # Нам не нужны входящие сообщения, но читаем их, чтобы корректно
            # обрабатывать disconnect.
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        await unregister(websocket)



async def on_startup():
    global redis_task
    redis_url = get_redis_url()
    if not redis_url:
        return
    channel = get_channel()

    async def _on_event(ev, payload):
        await broadcast(ev, payload)

    # Отдельная задача: слушаем Redis и пушим в локальные WS-клиенты
    redis_task = asyncio.create_task(
        subscribe_forever(redis_url=redis_url, channel=channel, on_event=_on_event)
    )


async def on_shutdown():
    global redis_task
    if redis_task is not None:
        redis_task.cancel()
        try:
            await redis_task
        except Exception:
            pass
        redis_task = None

app = Starlette(
    on_startup=[on_startup],
    on_shutdown=[on_shutdown],
    routes=[
        WebSocketRoute('/ws', ws_endpoint),
        Mount('/', app=flask_asgi),
    ]
)
