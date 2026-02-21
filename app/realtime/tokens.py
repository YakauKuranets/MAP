"""Realtime token helpers.

Используем подписанные токены (itsdangerous), чтобы не тащить куки-сессию
в WebSocket-рукопожатие и не открывать WS всем подряд.

Токен выдаётся только администратору через HTTP (`GET /api/realtime/token`).
"""

from __future__ import annotations

from typing import Any, Dict, Optional

from itsdangerous import URLSafeTimedSerializer, BadSignature, SignatureExpired


_SALT = "mapv12-realtime"


def _serializer(secret_key: str) -> URLSafeTimedSerializer:
    return URLSafeTimedSerializer(secret_key=secret_key, salt=_SALT)


def issue_token(secret_key: str, payload: Dict[str, Any]) -> str:
    """Выпустить токен (подписанный payload)."""
    return _serializer(secret_key).dumps(payload)


def verify_token(secret_key: str, token: str, *, max_age: int) -> Optional[Dict[str, Any]]:
    """Проверить токен. Возвращает payload или None."""
    try:
        data = _serializer(secret_key).loads(token, max_age=max_age)
        return data if isinstance(data, dict) else None
    except (BadSignature, SignatureExpired):
        return None
