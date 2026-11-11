import sqlite3
from datetime import datetime
from contextlib import contextmanager


class Database:
    def __init__(self, path="sms.db"):
        self.path = path
        self._init_db()

    def _init_db(self):
        with self._conn() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sender TEXT,
                    body TEXT,
                    received_at TEXT,
                    is_code INTEGER DEFAULT 0
                )
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_sender ON messages(sender)")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_received ON messages(received_at)")

    @contextmanager
    def _conn(self):
        conn = sqlite3.connect(self.path)
        conn.row_factory = sqlite3.Row
        try:
            yield conn
            conn.commit()
        finally:
            conn.close()

    def add_message(self, sender: str, body: str, is_code: bool = False):
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO messages (sender, body, received_at, is_code) VALUES (?, ?, ?, ?)",
                (sender, body, datetime.utcnow().isoformat() + "Z", 1 if is_code else 0)
            )

    def get_messages(self, limit=100, sender=None, search=None):
        query = "SELECT * FROM messages WHERE 1=1"
        params = []
        if sender:
            query += " AND sender LIKE ?"
            params.append(f"%{sender}%")
        if search:
            query += " AND body LIKE ?"
            params.append(f"%{search}%")
        query += " ORDER BY id DESC LIMIT ?"
        params.append(limit)
        with self._conn() as conn:
            rows = conn.execute(query, params).fetchall()
            return [dict(r) for r in rows]
