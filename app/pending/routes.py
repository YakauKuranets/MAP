"""Маршруты для работы с очередью заявок (pending markers).

Вся тяжёлая логика перенесена в :mod:`app.services.pending_service`,
здесь остаются только проверки прав и HTTP-обёртки.
"""

from __future__ import annotations

from flask import Response, jsonify, request

from ..helpers import require_admin, get_current_admin
from ..services.permissions_service import has_zone_access
from ..services.pending_service import (
    get_pending_count,
    list_pending_markers,
    approve_pending,
    reject_pending,
    clear_all_pending,
)
from ..models import PendingMarker
from . import bp


@bp.get('/count')
def pending_count() -> Response:
    """Публичный счётчик ожидающих заявок."""
    count = get_pending_count()
    return jsonify({'count': count})


@bp.get('')
def list_pending() -> Response:
    """Список ожидающих заявок (только для администратора).

    Если у администратора привязаны зоны, показываем только те
    заявки, у которых либо нет zone_id, либо zone_id входит в
    доступные зоны. superadmin видит все заявки.
    """
    require_admin("viewer")
    markers = list_pending_markers()

    admin = get_current_admin()
    if not admin:
        return jsonify(markers)

    filtered = []
    for m in markers:
        zid = m.get('zone_id')
        # Заявки без зоны видны всем, заявки с зоной — только тем,
        # у кого есть доступ к этой зоне (или superadmin).
        if zid is None or has_zone_access(admin, zid):
            filtered.append(m)

    return jsonify(filtered)


@bp.post('/<int:pid>/approve')
def pending_approve(pid: int) -> Response:
    """Одобрить заявку и перенести её в список адресов.

    Перед одобрением проверяем, что у администратора есть доступ
    к зоне заявки (если она указана). superadmin может одобрять
    любые заявки.
    """
    require_admin()
    pending = PendingMarker.query.get(pid)
    if not pending:
        return jsonify({'error': 'not found'}), 404

    if pending.zone_id is not None:
        admin = get_current_admin()
        if admin is None or not has_zone_access(admin, pending.zone_id):
            return jsonify({'error': 'forbidden'},), 403

    try:
        result = approve_pending(pid)
    except ValueError:
        return jsonify({'error': 'not found'}), 404
    return jsonify(result)


@bp.post('/<int:pid>/reject')
def pending_reject(pid: int) -> Response:
    """Отклонить заявку. Просто удалить её из очереди.

    Проверяем доступ администратора к зоне заявки (если указана).
    """
    require_admin()
    pending = PendingMarker.query.get(pid)
    if not pending:
        return jsonify({'error': 'not found'}), 404

    if pending.zone_id is not None:
        admin = get_current_admin()
        if admin is None or not has_zone_access(admin, pending.zone_id):
            return jsonify({'error': 'forbidden'},), 403

    try:
        result = reject_pending(pid)
    except ValueError:
        return jsonify({'error': 'not found'}), 404
    return jsonify(result)


@bp.post('/clear')
def pending_clear() -> Response:
    """Очистить очередь ожидания. Устанавливает статус cancelled для всех.

    Эта операция затрагивает все заявки во всех зонах, поэтому
    доступна только супер‑администратору.
    """
    require_admin(min_role="superadmin")
    result = clear_all_pending()
    return jsonify(result)