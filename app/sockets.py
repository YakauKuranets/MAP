"""
Модуль для организации WebSocket‑соединений.

Веб-сокеты позволяют серверу отправлять события клиентам в реальном
времени без необходимости опрашивать эндпоинт. Этот модуль
использует библиотеку `websockets` для запуска сервера и
предоставляет функции для вещания событий всем подключённым
клиентам. Сервер запускается в отдельном потоке, чтобы не
блокировать основной Flask‑приложение.

Пример использования:

    from threading import Thread
    from .sockets import start_socket_server, broadcast_event_sync

    # старт сервера
    thread = Thread(target=start_socket_server, args=('0.0.0.0', 8765), daemon=True)
    thread.start()

    # отправка события
    broadcast_event_sync('pending_created', {'id': 1})

На клиенте можно подключиться к ws://<host>:8765 и получать
сообщения в формате JSON: {"event":"pending_created","data":{...}}.
"""

import asyncio
import json
from typing import Dict, Any, Set, Optional

from urllib.parse import urlparse, parse_qs

import websockets

from .realtime.tokens import verify_token

# Список подключённых клиентов (websockets.WebSocketServerProtocol)
connected_clients: Set[websockets.WebSocketServerProtocol] = set()

# Цикл событий, который используется сервером websockets
ws_loop: asyncio.AbstractEventLoop | None = None

# Настройки безопасности WS (передаются через start_socket_server)
_ws_secret_key: Optional[str] = None
_ws_token_ttl: int = 600
_ws_allowed_origins_raw: str = ""
_ws_allowed_hostnames: Set[str] = set()


def _origin_allowed(origin: Optional[str]) -> bool:
    """Origin-check для WS.

    - Если REALTIME_ALLOWED_ORIGINS задан — требуем точное совпадение.
    - Если Origin отсутствует (CLI) — разрешаем.
    - Иначе разрешаем same-host (по hostname).
    """
    if not origin:
        return True
    # Если задан явный allowlist
    allow = [o.strip() for o in (_ws_allowed_origins_raw or "").split(",") if o.strip()]
    if allow:
        return origin in allow
    try:
        host = urlparse(origin).hostname
    except Exception:
        return False
    if not host:
        return False
    return host in _ws_allowed_hostnames


async def _handler(websocket, path: str | None = None):
    """Обработчик подключений. Сохраняет websocket в наборе
    подключенных клиентов и ожидает входящих сообщений. При
    отключении удаляет клиента."""
    # Совместимость websockets<12 (handler(websocket, path)) и websockets>=12 (handler(connection))
    try:
        if path is None:
            # websockets>=12: connection.request.path + connection.request.headers
            req = getattr(websocket, "request", None)
            path = getattr(req, "path", None) or getattr(websocket, "path", "/")
            headers = getattr(req, "headers", None) or getattr(websocket, "request_headers", None) or {}
        else:
            # websockets<12
            headers = getattr(websocket, "request_headers", None) or {}
    except Exception:
        headers = {}
        path = path or "/"

    # Origin check
    if not _origin_allowed((headers.get("Origin") if hasattr(headers, 'get') else None)):
        try:
            await websocket.close(code=1008, reason="Origin not allowed")
        finally:
            return

    # Token auth
    if not _ws_secret_key:
        try:
            await websocket.close(code=1011, reason="Server not configured")
        finally:
            return
    qs = parse_qs(urlparse(path).query)
    token = (qs.get("token") or [None])[0]
    payload = verify_token(_ws_secret_key, token or "", max_age=_ws_token_ttl) if token else None
    if not payload:
        try:
            await websocket.close(code=1008, reason="Unauthorized")
        finally:
            return

    connected_clients.add(websocket)
    try:
        async for _ in websocket:
            # В этой реализации сервер игнорирует входящие сообщения.
            # Можно добавить логику обработки чата, если необходимо.
            pass
    finally:
        connected_clients.discard(websocket)


async def _broadcast(event: str, data: Dict[str, Any]) -> None:
    """Рассылка события всем подключенным клиентам.

    Формирует JSON‑строку с полями `event` и `data` и отправляет её
    каждому websocket. Клиенты, у которых отправка не удалась,
    автоматически удаляются из набора подключённых.
    """
    if not connected_clients:
        return
    message = json.dumps({'event': event, 'data': data}, ensure_ascii=False)
    to_remove: Set[websockets.WebSocketServerProtocol] = set()
    for ws in list(connected_clients):
        try:
            await ws.send(message)
        except Exception:
            to_remove.add(ws)
    for ws in to_remove:
        connected_clients.discard(ws)


def broadcast_event_sync(event: str, data: Dict[str, Any]) -> None:
    """Синхронная обёртка для рассылки события.

    1) Если включён Redis Pub/Sub (REDIS_URL) — публикуем событие в Redis.
       Доставку до клиентов выполняет подписчик (ASGI/WS сервер).
    2) Иначе (или при ошибке Redis) — шлём локально: ASGI hub и/или
       standalone WS сервер (websockets).

    :param event: имя события
    :param data: словарь с данными
    """
    global ws_loop

    # Redis Pub/Sub (межпроцессная доставка)
    try:
        from .realtime.broker import get_broker

        payload = {'event': event, 'data': data}
        if get_broker().publish_event('map_updates', payload):
            return
    except Exception:
        pass

    # ASGI hub (если приложение запущено через asgi_realtime)
    try:
        from .realtime.hub import broadcast_sync as asgi_broadcast
        asgi_broadcast(event, data)
    except Exception:
        pass

    # Standalone WS server (websockets) — если запущен в этом процессе
    if ws_loop is None:
        return
    asyncio.run_coroutine_threadsafe(_broadcast(event, data), ws_loop)

def start_socket_server(
    host: str = '0.0.0.0',
    port: int = 8765,
    *,
    secret_key: Optional[str] = None,
    token_ttl: int = 600,
    allowed_origins: str = "",
) -> None:
    """Запустить WebSocket‑сервер.

    Сервер создаёт новый цикл событий, запускает обработчик
    подключений и блокирует поток. Предназначен для запуска в
    отдельном потоке. Если сервер уже запущен, повторный вызов
    перезапишет глобальный цикл `ws_loop`.

    :param host: адрес для прослушивания (по умолчанию 0.0.0.0)
    :param port: порт для прослушивания (по умолчанию 8765)
    """
    global ws_loop, _ws_secret_key, _ws_token_ttl, _ws_allowed_origins_raw, _ws_allowed_hostnames
    # Настройки безопасности (в dev можно передать через run.py из app.config)
    _ws_secret_key = secret_key
    _ws_token_ttl = int(token_ttl or 600)
    _ws_allowed_origins_raw = allowed_origins or ""
    # хостнеймы для same-origin проверки
    _ws_allowed_hostnames = set()
    if host and host not in {"0.0.0.0", "::"}:
        _ws_allowed_hostnames.add(host)
    _ws_allowed_hostnames.add("localhost")
    _ws_allowed_hostnames.add("127.0.0.1")
    _ws_allowed_hostnames.add("0.0.0.0")
    ws_loop = asyncio.new_event_loop()
    asyncio.set_event_loop(ws_loop)
    # Определяем корутину для запуска сервера внутри события петли
    async def _start():
        # Запускаем WS сервер
        await websockets.serve(_handler, host, port)

        # Redis Pub/Sub (опционально): подписываемся и ретранслируем события клиентам.
        try:
            from .realtime.broker import get_broker

            async def _on_message(payload):
                ev = payload.get('event')
                data = payload.get('data')
                if isinstance(ev, str) and isinstance(data, dict):
                    await _broadcast(ev, data)

            asyncio.create_task(get_broker().listener('map_updates', _on_message))
        except Exception:
            pass
    # Выполняем корутину один раз для инициализации сервера
    ws_loop.run_until_complete(_start())
    try:
        ws_loop.run_forever()
    finally:
        ws_loop.close()