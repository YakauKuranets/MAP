"""Audit API (superadmin only)."""

from __future__ import annotations

from flask import Blueprint, jsonify, request

from ..helpers import require_admin
from ..models import AdminAuditLog

bp = Blueprint('audit', __name__)


@bp.get('/')
def list_audit():
    require_admin(min_role='superadmin')
    try:
        limit = int(request.args.get('limit') or 200)
    except Exception:
        limit = 200
    limit = max(1, min(limit, 500))
    try:
        offset = int(request.args.get('offset') or 0)
    except Exception:
        offset = 0
    offset = max(0, offset)

    action = (request.args.get('action') or '').strip() or None
    actor = (request.args.get('actor') or '').strip() or None

    q = AdminAuditLog.query
    if action:
        q = q.filter(AdminAuditLog.action == action)
    if actor:
        q = q.filter(AdminAuditLog.actor == actor)

    rows = q.order_by(AdminAuditLog.ts.desc()).offset(offset).limit(limit).all()
    return jsonify([r.to_dict() for r in rows]), 200
