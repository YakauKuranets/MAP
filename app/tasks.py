"""Простейший механизм для фоновых задач.

Вместо полноценного брокера задач (Celery) используется
ThreadPoolExecutor, который выполняет задачи в отдельных
потоках. Это решение упрощает асинхронное выполнение
длительных операций, таких как генерация офлайн‑геокода или
скачивание тайлов, и не требует установки сторонних зависимостей.

Пример использования:

    from .tasks import submit_task
    def heavy_job(x):
        ...
    submit_task(heavy_job, 42)
"""

from __future__ import annotations

import os
from concurrent.futures import ThreadPoolExecutor, Future
from typing import Callable, Any

# Глобальный пул потоков для задач. Значение по умолчанию — 4 потока,
# его можно изменить через переменную окружения ``TASKS_MAX_WORKERS``.
_max_workers = int(os.getenv('TASKS_MAX_WORKERS', '4'))
_executor = ThreadPoolExecutor(max_workers=_max_workers)


def submit_task(fn: Callable[..., Any], *args: Any, **kwargs: Any) -> Future:
    """Отправить задачу на выполнение в отдельный поток.

    :param fn: вызываемая функция
    :param args: позиционные аргументы функции
    :param kwargs: именованные аргументы функции
    :return: объект Future для отслеживания статуса задачи
    """
    return _executor.submit(fn, *args, **kwargs)
