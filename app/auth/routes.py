"""
Маршруты для установки роли и входа администратора.

Используются cookie‑сессии для хранения роли пользователя
(admin или guest). Права администратора проверяются в
helpers.require_admin().
"""

import secrets

from flask import Response, abort, jsonify, request, session, current_app

from ..audit.logger import log_admin_action
from ..security.rate_limit import check_rate_limit
from werkzeug.security import check_password_hash
from ..services.permissions_service import verify_admin_credentials

from . import bp


@bp.post('/setrole/<role>')
def set_role(role: str) -> Response:
    """
    Установить роль пользователя.

    Гостевой режим отключён: переключение роли через этот роут запрещено.
    Роль 'admin' выставляется исключительно через /login.
    """
    abort(404)



@bp.post('/login')
def login() -> Response:
    """
    Вход администратора.

    Клиент отправляет JSON с полями 'username' и 'password'.

    Логика проверки:

    1) Сначала пытаемся найти администратора в таблице AdminUser
       (permissions_service.verify_admin_credentials).
    2) Если не нашли — используем legacy‑путь: сравнение с
       ADMIN_USERNAME / ADMIN_PASSWORD_HASH из конфигурации.

    При успешной проверке в сессии выставляются:

    - session['role'] = 'admin' (для совместимости со старым кодом);
    - session['admin_username'] = <username>;
    - session['admin_level'] = <role из AdminUser, если есть>;
    - session['username'] = <username> (как и раньше).
    """

    # --- Rate limit ---
    try:
        ip = (request.headers.get("X-Forwarded-For") or request.remote_addr or "unknown").split(",")[0].strip()
        limit = int(current_app.config.get("RATE_LIMIT_LOGIN_PER_MINUTE", 10))
        ok, info = check_rate_limit(bucket="login", ident=ip, limit=limit, window_seconds=60)
        if not ok:
            return jsonify(error="rate_limited", limit=info.limit, remaining=info.remaining, reset_in=info.reset_in), 429
    except Exception:
        # Никогда не ломаем логин из-за лимитера.
        pass

    data = request.get_json(silent=True) or {}
    username = (data.get('username') or '').strip()
    password = (data.get('password') or '').strip()

    # 1) Пытаемся аутентифицировать по базе админов
    admin = verify_admin_credentials(username, password)
    if admin is not None:
        session['role'] = 'admin'
        session['is_admin'] = True
        session.permanent = True
        session['admin_username'] = admin.username
        session['admin_level'] = admin.role
        session['username'] = admin.username
        log_admin_action('auth.login', {'username': username})
        return jsonify({'status': 'ok', 'role': admin.role}), 200

    # 2) Легаси-путь: один админ из конфига
    stored_user = current_app.config.get('ADMIN_USERNAME')
    stored_hash = current_app.config.get('ADMIN_PASSWORD_HASH')
    if username == stored_user and stored_hash and check_password_hash(stored_hash, password):
        session['role'] = 'admin'
        session['is_admin'] = True
        session.permanent = True
        session['username'] = username
        session['admin_username'] = username
        session['admin_level'] = 'superadmin'
        log_admin_action('auth.login', {'username': username})
        return jsonify({'status': 'ok', 'role': 'superadmin'}), 200

    return jsonify({'error': 'Invalid credentials'}), 401


@bp.post('/logout')
def logout() -> Response:
    """Выйти из админской сессии (очистить cookie-сессию)."""
    log_admin_action('auth.logout')
    session.clear()
    return ('', 204)


@bp.get('/me')
def me() -> Response:
    """Текущая сессия (удобно для UI/диагностики)."""
    return jsonify({
        'is_admin': bool(session.get('is_admin')),
        'role': session.get('admin_level') if session.get('is_admin') else (session.get('role') or 'guest'),
        'username': session.get('admin_username') or session.get('username'),
    }), 200