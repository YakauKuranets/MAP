"""ASGI-энтрипоинт для запуска приложения через uvicorn / hypercorn.

Работает через адаптер WSGI -> ASGI (asgiref.WsgiToAsgi), поэтому
можно использовать тот же Flask-приложение, что и для gunicorn.
"""

import os

from asgiref.wsgi import WsgiToAsgi

from app import create_app
from app.config import ProductionConfig


def get_config_class():
    cfg_name = os.environ.get("APP_CONFIG", "production").lower()
    if cfg_name in {"prod", "production"}:
        return ProductionConfig
    from app.config import DevelopmentConfig, TestingConfig
    if cfg_name in {"dev", "development"}:
        return DevelopmentConfig
    if cfg_name in {"test", "testing"}:
        return TestingConfig
    return ProductionConfig


flask_app = create_app(get_config_class())
# ASGI-приложение, которое можно отдавать uvicorn/hypercorn
app = WsgiToAsgi(flask_app)
