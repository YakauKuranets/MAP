"""Admin audit logging helpers (best-effort)."""

from __future__ import annotations

import json
from typing import Any, Dict, Optional

from flask import request, session

from ..extensions import db
from ..models import AdminAuditLog
from ..helpers import get_current_admin


def log_admin_action(action: str, payload: Optional[Dict[str, Any]] = None) -> None:
    """Записать аудит админского действия.

    Best-effort: не должен ломать основную логику, поэтому ошибки подавляются.
    """
    try:
        admin = get_current_admin()
        actor = None
        role = None
        if admin:
            actor = getattr(admin, 'username', None) or getattr(admin, 'login', None)
            role = getattr(admin, 'role', None) or getattr(admin, 'level', None)
        actor = actor or session.get('admin_username') or session.get('username')
        role = role or session.get('admin_level') or session.get('role')

        # IP: учитываем reverse-proxy
        ip = (request.headers.get('X-Forwarded-For') or '').split(',')[0].strip() or request.remote_addr

        row = AdminAuditLog(
            actor=actor,
            role=role,
            ip=ip,
            method=request.method,
            path=request.path,
            action=action,
            payload_json=json.dumps(payload, ensure_ascii=False) if payload else None,
        )
        db.session.add(row)
        db.session.commit()
    except Exception:
        try:
            db.session.rollback()
        except Exception:
            pass
