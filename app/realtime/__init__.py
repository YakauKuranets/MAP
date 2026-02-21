"""Blueprint realtime.

Маршруты:

- ``GET /api/realtime/token`` — выдать токен для подключения к WebSocket/SSE.

Токен выдаётся только администратору (require_admin).
"""

from flask import Blueprint


bp = Blueprint("realtime", __name__, url_prefix="/api/realtime")


from . import routes  # noqa: E402,F401
